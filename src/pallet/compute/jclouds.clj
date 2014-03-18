(ns pallet.compute.jclouds
  "jclouds compute service implementation."
  (:use
   [clojure.reflect :only [reflect]]
   [clojure.stacktrace :only [root-cause]]
   [clojure.string :only [lower-case]]
   [clojure.tools.logging :only [debugf tracef warnf]])
  (:require
   [com.palletops.jclouds.compute2 :as jclouds]
   [pallet.compute.implementation :as implementation]
   [pallet.compute.jvm :as jvm]
   [pallet.compute :as compute]
   [pallet.environment :as environment]
   [pallet.node :as node]
   [pallet.script :as script]
   [pallet.utils :as utils]
   [pallet.execute :as execute]
   [clojure.string :as string]
   [clojure.tools.logging :as logging])
  (:import
   [org.jclouds.compute.domain.internal HardwareImpl ImageImpl NodeMetadataImpl]
   org.jclouds.compute.ComputeServiceContext
   org.jclouds.compute.ComputeService
   org.jclouds.compute.options.RunScriptOptions
   org.jclouds.compute.options.TemplateOptions
   [org.jclouds.compute.domain
    NodeMetadata Image OperatingSystem OsFamily Hardware Template
    HardwareBuilder NodeMetadataBuilder ImageBuilder]
   [org.jclouds.domain Location LoginCredentials]
   org.jclouds.io.Payload
   org.jclouds.scriptbuilder.domain.Statement
   com.google.common.base.Predicate))

(try
  (use '[pallet.core.user :only [make-user]])
  (catch Exception _
    (use '[pallet.utils :only [make-user]])))

;; feature predicates
(defmacro ^{:private true} get-has-feature
  []
  (try
    (do
      (require 'pallet.feature)
      (when-not (ns-resolve 'pallet.compute.jclouds 'has-feature?)
        (use '[pallet.feature
               :only [has-feature? when-feature when-not-feature]])))
    (catch Exception e
      `(do
         (defmacro ^{:private true} has-feature? [_#] false)
         (defmacro ^{:private true} when-not-feature [_# & ~'body]
           `(do ~@~'body))
         (defmacro ^{:private true} when-feature [_# & ~'body]
           nil)))))

(get-has-feature)

;;; Meta
(defn supported-providers []
  (set
   (map #(.getId %)
        (concat
         (org.jclouds.apis.Apis/viewableAs ComputeServiceContext)
         (org.jclouds.providers.Providers/viewableAs ComputeServiceContext)))))

;;;; Compute service
(defn default-jclouds-extensions
  "Default extensions"
  [provider]
  (concat
   (if (jvm/log4j?) [:log4j] [])
   (if (jvm/slf4j?) [:slf4j] [])
   (if (= (name provider) "stub")
     (try
       (require 'pallet.compute.jclouds-ssh-test)
       (when-let [f (ns-resolve
                     'pallet.compute.jclouds-ssh-test 'ssh-test-client)]
         [(f (ns-resolve 'pallet.compute.jclouds-ssh-test 'no-op-ssh-client))])
       (catch java.io.FileNotFoundException _))
     (try
       (Class/forName "org.jclouds.sshj.config.SshjSshClientModule")
       (logging/debugf "Found jclouds sshj driver")
       [:sshj]
       (catch ClassNotFoundException _
         (try
           (Class/forName "org.jclouds.ssh.jsch.config.JschSshClientModule")
           (logging/debugf "Found jclouds jsch driver")
           [:jsch]
           (catch ClassNotFoundException _
             (logging/warnf
              (str "Could not find a jclouds ssh client module. "
                   "Add org.jclouds.driver/jclouds-sshj or jclouds-jsch "
                   "to your project dependencies.")))))))))

(def ^{:private true :doc "translate option names"}
  option-keys
  {:endpoint :jclouds.endpoint})

(defn option-key
  [provider key]
  (case key
    :endpoint (keyword (format (str provider ".endpoint")))
    (option-keys key key)))

(defn hardware-impl
  [provider-id name id location uri user-metadata tags processors ram volumes
   image-supported-fn]
  (.. (HardwareBuilder.)
      (providerId provider-id)
      (name name)
      (id id)
      (location location)
      (uri uri)
      (userMetadata user-metadata)
      (tags tags)
      (processors processors)
      (ram ram)
      (volumes volumes)
      (supportsImage image-supported-fn)
      build))

(defn node-metadata-impl
  [provider-id name id location uri user-metadata tags
   group-name hardware image-id os state login-port
   public-ips private-ips admin-password credentials hostname]
  (let [credentials (if admin-password
                      (..
                       (if credentials
                         (LoginCredentials/builder credentials)
                         (LoginCredentials/builder))
                       (password admin-password)
                       build)
                      credentials)]
    (.. (NodeMetadataBuilder.)
        (providerId provider-id)
        (name name)
        (id id)
        (location location)
        (uri uri)
        (userMetadata user-metadata)
        (tags tags)
        (group group-name)
        (hardware hardware)
        (imageId image-id)
        (operatingSystem os)
        (status state)
        (loginPort login-port)
        (publicAddresses public-ips)
        (privateAddresses private-ips)
        (credentials credentials)
        (hostname hostname)
        build)))

(defn image-impl
  [provider-id name id location uri user-metadata tags
   os description version admin-password credentials]
  (.. (ImageBuilder.)
      (providerId provider-id)
      (name name)
      (id id)
      (location location)
      (uri uri)
      (userMetadata user-metadata)
      (tags tags)
      (operatingSystem os)
      (description description)
      (version version)
      (adminPassword admin-password)
      (credentials credentials)
      build))

(defn make-operating-system
  [{:keys [family name version arch description is-64bit]
    :or {family OsFamily/UBUNTU
         name "Ubuntu"
         version "Some version"
         arch "Some arch"
         description "Desc"
         is-64bit true}}]
  (.. (org.jclouds.compute.domain.OperatingSystem$Builder.)
      (family family)
      (name name)
      (version version)
      (arch arch)
      (description description)
      (is64Bit is-64bit)
      build))

(def jvm-os-family-map
  {"AIX" OsFamily/AIX
   "ARCH" OsFamily/ARCH
   "Mac OS" OsFamily/DARWIN
   "Mac OS X" OsFamily/DARWIN
   "FreeBSD" OsFamily/FREEBSD
   "HP UX" OsFamily/HPUX
   "Linux"   OsFamily/UBUNTU ;; guess for now
   "Solaris" OsFamily/SOLARIS
   "Windows 2000" OsFamily/WINDOWS
   "Windows 7" OsFamily/WINDOWS
   "Windows 95" OsFamily/WINDOWS
   "Windows 98" OsFamily/WINDOWS
   "Windows NT" OsFamily/WINDOWS
   "Windows Vista" OsFamily/WINDOWS
   "Windows XP" OsFamily/WINDOWS})

(defn local-operating-system
  "Create an OperatingSystem object for the local host"
  []
  (let [os-name (System/getProperty "os.name")]
    (make-operating-system
     {:family (or (jvm-os-family-map (jvm/os-name)) OsFamily/UNRECOGNIZED)
      :name os-name
      :description os-name
      :version (System/getProperty "os.version")
      :arch (System/getProperty "os.arch")
      :is-64bit (= "64" (System/getProperty "sun.arch.data.model"))})))

(defn make-hardware
  [{:keys [provider-id name id location uri user-metadata processors ram
           volumes supports-image tags]
    :or {provider-id "provider-hardware-id"
         name "Some Hardware"
         id "Some id"
         user-metadata {}
         processors []
         ram 512
         volumes []
         supports-image (fn [&] true)
         tags (java.util.HashSet.)}}]
  (let [image-supported-fn (reify com.google.common.base.Predicate
                             (apply [_ i] (supports-image i))
                             (equals [_ i] (= supports-image i)))]
    (hardware-impl provider-id name id location uri user-metadata tags
                   processors ram volumes image-supported-fn)))

(defn local-hardware
  "Create an Hardware object for the local host"
  []
  (let [os-name (System/getProperty "os.name")]
    (make-hardware {})))


(defn make-image
  [id & options]
  (let [options (apply hash-map options)
        meta (dissoc options :name :location :uri :user-metadata
                     :version :operating-system :default-credentials
                     :description)]
    (image-impl
     id ; providerId
     (options :name)
     id
     (options :location)
     (options :uri)
     (merge (get options :user-metadata {}) meta)
     (options :tags #{})
     (options :operating-system)
     (options :description "image description")
     (options :version "image version")
     (options :admin-password)
     (options :default-credentials))))

(defn compute-node? [object]
  (instance? NodeMetadata object))

(deftype JcloudsNode
    [^org.jclouds.compute.domain.NodeMetadata node service]
  pallet.node/Node
  (ssh-port
    [node]
    (let [md (into {} (.getUserMetadata node))
          port (:ssh-port md)]
      (if port
        (Integer. port)
        (Integer. 22))))

  (primary-ip [node] (first (jclouds/public-ips node)))
  (private-ip [node] (first (jclouds/private-ips node)))
  (is-64bit? [node] (.. node getOperatingSystem is64Bit))
  (group-name [node] (jclouds/group node))

  (os-family
    [node]
    (when-let [operating-system (.getOperatingSystem node)]
      (keyword (str (.getFamily operating-system)))))

  (os-version
    [node]
    (when-let [operating-system (.getOperatingSystem node)]
      (let [version (.getVersion operating-system)]
        (when-not (string/blank? version)
          version))))

  (hostname [node] (.getName node))
  (id [node] (.getId node))
  (running? [node] (jclouds/running? node))
  (terminated? [node] (jclouds/terminated? node))
  (compute-service [node] service)

  org.jclouds.compute.domain.NodeMetadata
  ;; ResourceMetadata
  (getType [_] (.getType node))
  (getProviderId [_] (.getProviderId node))
  (getName [_] (.getName node))
  (getLocation [_] (.getLocation node))
  (getUri [_] (.getUri node))
  (getUserMetadata [_] (.getUserMetadata node))
  ;; ComputeMetadata
  (getId [_] (.getId node))
  (getTags [_] (.getTags node))
  ;; NodeMetadata
  ;; (getHostname [_] (.getHostname node))
  (getGroup [_] (.getGroup node))
  (getHardware [_] (.getHardware node))
  (getImageId [_] (.getImageId node))
  (getOperatingSystem [_] (.getOperatingSystem node))
  (getLoginPort [_] (.getLoginPort node))
  ;; (getAdminPassword [_] (.getAdminPassword node))
  ;; (getCredentials [_] (.getCredentials node))
  (getPublicAddresses [_] (.getPublicAddresses node))
  (getPrivateAddresses [_] (.getPrivateAddresses node))

  org.jclouds.compute.domain.ComputeMetadataIncludingStatus
  (getStatus [_] (.getStatus node)))

(when-feature node-packager
  (extend-type JcloudsNode
    pallet.node/NodePackager
    (packager [n]
      (try
        (compute/packager-for-os (node/os-family n) (node/os-version n))
        (catch Exception e
          (tracef e "Failed to determine packager for node")
          (debugf "Failed to determine packager for node"))))))

(when-feature node-image
  (extend-type JcloudsNode
    pallet.node/NodeImage
    (image-user [node]
      (if-let [credentials (.getCredentials (.node node))]
        (do
          (logging/debugf
           "Node credentials %s" (bean credentials))
          (logging/debugf
           "  should auth sudo %s" (.shouldAuthenticateSudo credentials))
          (make-user
           (.getUser credentials)
           {:password (.getPassword credentials)
            :private-key (.getPrivateKey credentials)
            :sudo-password (when (.shouldAuthenticateSudo credentials)
                             (.getPassword credentials))}))))))

(when-feature node-hardware
  (extend-type JcloudsNode
    pallet.node/NodeHardware
    (hardware [node]
      (let [beanf (comp #(dissoc % :class) bean)
            hw (.getHardware (.node node))
            b (beanf hw)]
        (logging/debugf "Node hardware %s" (bean hw))
        (logging/debugf "     volumes sizes %s" (->>
                                                 (.. hw getVolumes)
                                                 (map #(.getSize %))
                                                 vec))
        (-> b
            (select-keys [:hypervisor :providerId :id :ram])
            (assoc :cpus (vec (map beanf (:processors b))))
            (assoc :disks
              (->>
               (:volumes b)
               (map beanf)
               (map #(update-in % [:size] (fn [s] (or s 8))))
               (map #(update-in % [:type] (comp keyword lower-case str)))
               vec)))))))

(when-feature node-proxy
  (extend-type JcloudsNode
    pallet.node/NodeProxy
    (proxy [node])))

(defn jclouds-node->node [service node]
  (JcloudsNode. node service))

(defn make-node [group-name & options]
  (let [options (apply hash-map options)]
    (jclouds-node->node
     (:service options)
     (node-metadata-impl
      (options :provider-id (options :id group-name))
      (options :name group-name)        ; name
      (options :id group-name)          ; id
      (options :location)
      (java.net.URI. group-name)        ; uri
      (options :user-metadata {})
      (options :tags #{})
      group-name
      (if-let [hardware (options :hardware)]
        (if (map? hardware) (make-hardware hardware) hardware)
        (make-hardware {}))
      (options :image-id)
      (if-let [os (options :operating-system)]
        (if (map? os) (make-operating-system os) os)
        (make-operating-system {}))
      (options :state org.jclouds.compute.domain.NodeMetadata$Status/RUNNING)
      (options :login-port 22)
      (options :public-ips [])
      (options :private-ips [])
      (options :admin-password)
      (options :credentials nil)
      (options :hostname (str (gensym group-name)))))))

(defn make-unmanaged-node
  "Make a node that is not created by pallet's node management.
   This can be used to manage configuration of any machine accessable over
   ssh, including virtual machines."
  [group-name host-or-ip & options]
  (let [options (apply hash-map options)
        meta (dissoc options :location :user-metadata :state :login-port
                     :public-ips :private-ips :extra :admin-password
                     :credentials :hostname)]
    (jclouds-node->node
     (:service options)
     (node-metadata-impl
      (options :provider-id (options :id group-name))
      (options :name group-name)
      (options :id (str group-name (rand-int 65000)))
      (options :location)
      (java.net.URI. group-name)        ; uri
      (merge (get options :user-metadata {}) meta)
      (options :tags #{})
      group-name
      (if-let [hardware (options :hardware)]
        (if (map? hardware) (make-hardware hardware) hardware)
        (make-hardware {}))
      (options :image-id)
      (if-let [os (options :operating-system)]
        (if (map? os) (make-operating-system os) os)
        (make-operating-system {}))
      (get options :state org.jclouds.compute.domain.NodeMetadata$Status/RUNNING)
      (options :login-port 22)
      (conj (get options :public-ips []) host-or-ip)
      (options :private-ips [])
      (options :admin-password)
      (options :credentials nil)
      (options :hostname (str (gensym group-name)))))))

(defn- build-node-template
  "Build the template for specified target node and compute context"
  [compute group public-key-path init-script]
  {:pre [(map? group) (:group-name group)]}
  (logging/debug (str "building node template for " (:group-name group)))
  (when public-key-path
    (logging/debug (str "  authorizing " public-key-path)))
  (when init-script
    (logging/debug (str "  init script\n" init-script)))
  (let [;; when we have an id, just use that so other keys don't prevent a
        ;; match
        group-spec (update-in group [:image]
                              #(if (:image-id %)
                                 (apply
                                  dissoc %
                                  (remove
                                   (fn [kw] (= :image-id kw))
                                   (keys @#'jclouds/template-map)))
                                 %))
        options (->> [:image :hardware :location :network :qos]
                     (select-keys group-spec)
                     vals
                     (reduce merge))
        options (if (:default-os-family group)
                  (dissoc options :os-family) ; remove if we added in
                                              ; ensure-os-family
                  options)]
    (logging/debug (str "  options " options))
    (let [options (if (and public-key-path
                           (not (:authorize-public-key options)))
                    (assoc options
                      :authorize-public-key (slurp public-key-path))
                    options)
          options (if (not (:run-script options))
                    (if init-script
                      (assoc options :run-script init-script)
                      (assoc options :run-script ""))  ; force wait on ssh
                    options)]
      (jclouds/build-template compute options))))

(deftype JcloudsService
    [^org.jclouds.compute.ComputeService compute
     environment provider-kw tag-provider]

  ;; implement jclouds ComputeService by forwarding
  org.jclouds.compute.ComputeService
  (getContext [_] (.getContext compute))
  (templateBuilder [_] (.templateBuilder compute))
  (templateOptions [_] (.templateOptions compute))
  (listHardwareProfiles [_] (.listHardwareProfiles compute))
  (listImages [_] (.listImages compute))
  (listNodes [_] (.listNodes compute))
  (listAssignableLocations [_] (.listAssignableLocations compute))
  ;; (createNodesInGroup [_ group count template]
  ;;                     (.createNodesInGroup compute group count template))
  ;; (createNodesInGroup [_ group count]
  ;;                     (.createNodesInGroup compute group count))
  (resumeNode [_ id] (.resumeNode compute id))
  (resumeNodesMatching [_ predicate] (.resumeNodesMatching compute predicate))
  (suspendNode [_ id] (.suspendNode compute id))
  (suspendNodesMatching [_ predicate] (.suspendNodesMatching compute predicate))
  (rebootNode [_ id] (.rebootNode compute id))
  (rebootNodesMatching [_ predicate] (.rebootNodesMatching compute predicate))
  (getNodeMetadata [_ id] (.getNodeMetadata compute id))
  (listNodesDetailsMatching [_ predicate]
    (.listNodesDetailsMatching compute predicate))
  (^java.util.Map runScriptOnNodesMatching
    [_ ^Predicate predicate ^String script]
    (.runScriptOnNodesMatching compute predicate script))
  (^java.util.Map runScriptOnNodesMatching
    [_ ^Predicate predicate ^Statement script]
    (.runScriptOnNodesMatching compute predicate script))
  (^java.util.Map runScriptOnNodesMatching
    [_ ^Predicate predicate ^String script ^RunScriptOptions options]
    (.runScriptOnNodesMatching compute predicate script options))
  (^java.util.Map runScriptOnNodesMatching
    [_ ^Predicate predicate ^Statement script ^RunScriptOptions options]
    (.runScriptOnNodesMatching compute predicate script options))

  pallet.compute.ComputeService
  (nodes
    [service]
    (map
     (partial jclouds-node->node service)
     (jclouds/nodes-with-details compute)))

  (ensure-os-family
    [_ group]
    (if (-> group :image :os-family)
      group
      (let [template (jclouds/build-template compute (:image group))
            family (-> (.. template getImage getOperatingSystem getFamily)
                       str keyword)]
        (logging/infof "OS is %s" (pr-str family))
        (when (or (nil? family) (= family OsFamily/UNRECOGNIZED))
          (throw
           (Exception.
            (format
             (str "jclouds was unable to determine the os-family "
                  "of the template %s")
             (pr-str (:image group))))))
        (->
         group
         (assoc-in [:image :os-family] family)
         (assoc-in [:default-os-family] true)))))

  (run-nodes
    [service group-spec node-count user init-script options]
    (letfn [(process-failed-start-nodes
              [e]
              (let [bad-nodes (.getNodeErrors e)]
                (logging/warnf
                 "Failed to start %s of %s nodes for group %s"
                 (count bad-nodes)
                 node-count
                 (:group-name group-spec))
                (doseq [[node e] bad-nodes]
                  (logging/errorf
                   e
                   "Failed to start node for group %s %s"
                   (:group-name group-spec) (.getMessage (root-cause e))))
                (doseq [node (keys bad-nodes)]
                  (try
                    (compute/destroy-node
                     service
                     (jclouds-node->node service node))
                    (catch Exception e
                      (logging/warnf
                       e
                       "Exception while trying to remove failed nodes for %s"
                       (:group-name group-spec)))))
                (->>
                 (.getSuccessfulNodes e)
                 (map (partial jclouds-node->node service))
                 (filter node/running?))))]
      (let [template (build-node-template
                      compute
                      group-spec
                      (:public-key-path user)
                      init-script)]
        (logging/debugf "Jclouds template %s" (str template))
        (try
          (->>
           (jclouds/create-nodes
            compute (name (:group-name group-spec)) node-count template)
           (map (partial jclouds-node->node service))
           ;; The following is a workaround for terminated nodes.
           ;; See http://code.google.com/p/jclouds/issues/detail?id=501
           (filter node/running?))
          (catch org.jclouds.compute.RunNodesException e
            (process-failed-start-nodes e))))))

  (reboot
    [_ nodes]
    (doseq [node nodes]
      (jclouds/reboot-node compute node)))

  (boot-if-down
    [_ nodes]
    (map #(jclouds/reboot-node compute %)
         (filter jclouds/terminated? nodes)))

  (shutdown-node
    [_ node user]
    (let [ip (node/primary-ip node)]
      (if ip
        nil
        ;; (execute/remote-sudo ip "shutdown -h 0" user {:pty false})
        )))

  (shutdown
    [self nodes user]
    (doseq [node nodes]
      (compute/shutdown-node self node user)))

  (destroy-nodes-in-group
    [_ group-name]
    (jclouds/destroy-nodes-matching
     compute
     #(= (jclouds/group %) (name group-name))))

  (destroy-node
    [_ node]
    (jclouds/destroy-node compute (node/id node)))

  (images [_] (jclouds/images compute))

  (close [_] (.. compute getContext close))

  pallet.environment.Environment
  (environment [_] environment))


(defmacro if-has-credential-supplier [if-form then-form]
  (if (try
        (import 'org.jclouds.rest.annotations.Credential)
        true
        (catch Exception e
          (logging/debug
           "Failed to find credentials annotation, using credentials supplier")
          (logging/trace e "Failed to find credentials annotation")
          (import 'pallet.jclouds.Tokens)
          nil))
    then-form
    if-form))

(if-has-credential-supplier
 (defn credentials-provider [injector]
   (.getInstance injector
                 (com.google.inject.Key/get
                  pallet.jclouds.Tokens/CREDENTIALS_SUPPLIER
                  org.jclouds.location.Provider)))
 (defn credentials-provider [injector]
   (.getInstance injector
                 (com.google.inject.Key/get
                  java.lang.String
                  org.jclouds.rest.annotations.Credential))))

(when-feature compute-service-properties
  (defn compute-service-properties
    "Return a map with the service details"
    [^org.jclouds.compute.ComputeService compute-service provider-kw]
    (let [context (.. compute-service getContext unwrap)
          credential (credentials-provider (.. context utils injector))]
      {:provider provider-kw
       :identity (.getIdentity context)
       :credential credential
       :endpoint (.. context getProviderMetadata getEndpoint)}))

  (extend-type JcloudsService
    pallet.compute/ComputeServiceProperties
    (service-properties [compute]
      (compute-service-properties (.compute compute) (.provider_kw compute)))))


(defn resource-id [node]
  (.getId node))

(defn local-resource-id [node]
  (let [id (.getId node)]
    (subs id (inc (.indexOf id "/")))))

(defn member-with-name [obj member-name]
  (->> (reflect obj)
       :members
       (filter #(= (:name %) name))
       first))

(defmacro add-node-tag []
  (if (has-feature? taggable-nodes)
    '(do
       (defn tag-filter [m]
         (com.google.common.collect.Multimaps/forMap
          m))

       (deftype JcloudsNodeTag [apis]
         pallet.compute.NodeTagReader
         (node-tag [_ node tag-name]
           (debugf "node-tag %s %s" (resource-id node) tag-name)
           (if-let [api (get apis (.. node getLocation getParent getId))]
             (let [tags (.. api
                            (filter (tag-filter
                                     {"resource-id" (local-resource-id node)
                                      "key" (name tag-name)}))
                            (toList))]
               (when (seq tags)
                 (let [v (.getValue (first tags))]
                   (when (.isPresent v)
                     (.get v)))))
             (warnf "node-tag tagging not supported for %s"
                    (resource-id node))))
         (node-tag [_ node tag-name default-value]
           (debugf "node-tag %s %s %s"
                   (resource-id node) tag-name default-value)
           (if-let [api (get apis (.. node getLocation getParent getId))]
             (let [tags (.. api
                            (filter (tag-filter
                                     {"resource-id" (local-resource-id node)
                                      "key" (name tag-name)}))
                            (toList))]
               (if (seq tags)
                 (let [v (.getValue (first tags))]
                   (if (.isPresent v)
                     (.get v)
                     default-value))
                 default-value))
             (warnf "node-tag tagging not supported for %s"
                    (resource-id node))))
         (node-tags [_ node]
           (debugf "node-tags %s" (resource-id node))
           (if-let [api (get apis (.. node getLocation getParent getId))]
             (into {} (map
                       #(vector (.getKey %) (let [v (.getValue %)]
                                              (when (.isPresent v)
                                                (.get v))))
                       (.. api
                           (filter
                            (tag-filter
                             {"resource-id" (local-resource-id node)}))
                           (toList))))
             (warnf "node-tags tagging not supported for %s"
                    (resource-id node))))

         pallet.compute.NodeTagWriter
         (tag-node! [_ node tag-name value]
           (debugf "tag-node! %s %s %s" (resource-id node) tag-name value)
           (if-let [api (get apis (.. node getLocation getParent getId))]
             (.applyToResources
              api
              (java.util.HashMap. {(name tag-name) (name value)})
              #{(local-resource-id node)})
             (warnf "tag-node! tagging not supported for %s"
                    (resource-id node))))
         (node-taggable? [_ node]
           (debugf "node-taggable? %s" (resource-id node))
           (get apis (.. node getLocation getParent getId))))

       (extend-type JcloudsService
         pallet.compute/NodeTagReader
         (node-tag
           ([compute node tag-name]
              (when-let [p (.tag_provider compute)]
                (compute/node-tag p node tag-name)))
           ([compute node tag-name default-value]
              (if-let [p (.tag_provider compute)]
                (compute/node-tag p node tag-name default-value)
                default-value)))
         (node-tags [compute node]
           (when-let [p (.tag_provider compute)]
             (compute/node-tags p node)))
         pallet.compute/NodeTagWriter
         (tag-node! [compute node tag-name value]
           (compute/tag-node! (.tag_provider compute) node tag-name value))
         (node-taggable? [compute node]
           (when (.tag_provider compute)
             (compute/node-taggable? (.tag_provider compute) node))))

       (defn default-tag-provider [service]
         (logging/debugf "default-tag-provider looking for tag service")
         (try
           (JcloudsNodeTag.
            (let [regions (.. service getContext unwrap getApi
                              getConfiguredRegions)]
              (logging/debugf "Regions for compute service %s" regions)
              (into {}
                    (map
                     #(let [api (.. service getContext
                                    unwrap getApi
                                    (getTagApiForRegion %))]
                        (if (.isPresent api)
                          (do (logging/debugf "Found tag api for region %s" %)
                              [% (.get api)])
                          (logging/warnf
                           "Failed to find tag api for region %s" %)))
                     regions))))
           (catch java.lang.IllegalArgumentException e
             (logging/debugf e "TagApi not supported")
             (logging/tracef e "While trying to get TagApi")))))
    `(defn ~'default-tag-provider [_#] nil)))

(add-node-tag)

(defn node-locations
  "Return locations of a node as a seq."
  [#^NodeMetadata node]
  (letfn [(loc [#^Location l]
               (when l (cons l (loc (.getParent l)))))]
    (loc (.getLocation node))))

(defn image-string
  [#^Image image]
  (when image
    (let [name (.getName image)
          description (.getDescription image)]
      (format "%s %s %s %s"
              (.getFamily (.getOperatingSystem image))
              (.getArch (.getOperatingSystem image))
              name
              (if (= name description) "" description)))))

(defn os-string
  [#^OperatingSystem os]
  (when os
    (let [name (.getName os)
          description (.getDescription os)]
      (format "%s %s %s %s"
              (.getFamily os)
              (.getArch os)
              name
              (if (= name description) "" description)))))

(defn location-string
  [#^Location location]
  (when location
    (format "%s/%s" (.getScope location) (.getId location))))

(defmethod clojure.core/print-method Location
  [location writer]
  (.write writer (location-string location)))

(defmethod clojure.core/print-method NodeMetadata
  [node writer]
  (.write
   writer
   (format
    "%14s\t %s %s\n\t\t %s\n\t\t %s\n\t\t public: %s  private: %s"
    (jclouds/group node)
    (apply str (interpose "." (map location-string (node-locations node))))
    (let [location (.getLocation node)]
      (when (and location
                 (not (= (.getDescription location) (.getId location))))
        (.getDescription location)))
    (os-string (.getOperatingSystem node))
    (.getStatus node)
    (apply
     str (interpose ", " (.getPublicAddresses node)))
    (apply
     str (interpose ", " (.getPrivateAddresses node))))))

(def jvm-os-map
     { "Mac OS X" :os-x })

(defn make-localhost-node
  "Make a node representing the local host"
  []
  (make-node "localhost"
             :public-ips ["127.0.0.1"]
             :operating-system (local-operating-system)))

(defn local-session
  "Create a session map for localhost"
  []
  (let [node (make-localhost-node)]
    {:all-nodes [node]
     :server {:image [(get jvm-os-map (System/getProperty "os.name"))]
              :node node}}))

;; service factory implementation for jclouds
(defmethod implementation/service :default
  [provider {:keys [identity credential extensions endpoint environment
                    tag-provider]
             :or {extensions (default-jclouds-extensions provider)}
             :as options}]
  (logging/debugf "extensions %s" (pr-str extensions))
  (let [options (dissoc
                 options
                 :identity :credential :extensions :blobstore :environment)
        options (interleave
                 (map #(option-key provider %) (keys options))
                 (vals options))
        service (apply
                 jclouds/compute-service
                 (name provider) identity credential
                 :extensions extensions
                 options)]
    (logging/debugf "options %s" (pr-str (vec options)))
    (JcloudsService.
     service
     environment
     (keyword (name provider))
     (or tag-provider (default-tag-provider service)))))
