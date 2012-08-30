(ns pallet.compute.jclouds-ssh-test
  (:require
   [clojure.tools.logging :as logging])
  (:import
   org.jclouds.domain.Credentials
   org.jclouds.domain.LoginCredentials
   org.jclouds.compute.domain.ExecResponse
   org.jclouds.io.Payload
   org.jclouds.ssh.SshClient
   com.google.common.net.HostAndPort))

(defn jclouds-features
  []
  (let [has-login-credentials
        (try
          (Class/forName "org.jclouds.domain.LoginCredentials")
          true
          (catch ClassNotFoundException _))
        bootstrap-expects-zero-response
        (try
          (Class/forName
           "org.jclouds.compute.callables.BlockUntilInitScriptStatusIsZeroThenReturnOutput")
          true
          (catch ClassNotFoundException _))]
    (hash-map
     :has-login-credentials has-login-credentials
     :bootstrap-expects-zero-response bootstrap-expects-zero-response)))

(defn instantiate [impl-class & args]
  (let [constructor (first
                     (filter
                      (fn [c] (= (count args) (count (.getParameterTypes c))))
                      (.getDeclaredConstructors impl-class)))]
    (.newInstance impl-class (object-array args))))




;; define an instance or implementation of the following interfaces:

(defn maybe-invoke [f & args]
  (when f
    (apply f args)))

(defn default-exec
  "Default exec function - replies to ./runscript status by returning success"
  [cmd]
  (merge
   {:exit 0 :err "" :out ""}
   (condp = cmd
       "/tmp/init-bootstrap status" {:exit 1}
       "/tmp/init-bootstrap exitstatus" {:out "0"}
       {})))

(deftype NoOpClient
    [socket username password]
  SshClient
  (connect [this])
  (disconnect [this])
  (exec [this cmd]
    (logging/debugf "ssh cmd: %s" cmd)
    (let [{:keys [out err exit] :as response} (default-exec cmd)]
      (logging/debugf "ssh response: %s" response)
      (ExecResponse. out err exit)))
  (get [this path] )
  (^void put [this ^String path ^String content])
  (^void put [this ^String path ^org.jclouds.io.Payload content])
  (getUsername [this] username)
  (getHostAddress [this] (.toString socket)))

(defn no-op-ssh-client
  [socket username password]
  (NoOpClient. socket username password))

(deftype SshClientFactory
    [factory-fn]
  org.jclouds.ssh.SshClient$Factory
  (^org.jclouds.ssh.SshClient
    create
    [_ ^HostAndPort socket ^Credentials credentials]
    (factory-fn socket (.identity credentials) (.credential credentials)))
  (^org.jclouds.ssh.SshClient
    create
    [_ ^HostAndPort socket ^LoginCredentials credentials]
    (factory-fn socket (.identity credentials) (.credential credentials))))

(deftype Module
    [factory binder]
  com.google.inject.Module
  (configure
   [this abinder]
   (reset! binder abinder)
   (.. @binder (bind org.jclouds.ssh.SshClient$Factory)
       (toInstance factory))))

(defn ssh-test-module
  "Create a module that specifies the factory for creating a test service"
  [factory]
  (let [binder (atom nil)]
    (Module. factory binder)))

(defn ssh-test-client
  "Create a module that can be passed to a compute-context, and which implements
an ssh client with the provided map of function implementations.  Keys are
clojurefied versions of org.jclouds.ssh.SshClient's methods"
  [factory-fn]
  (ssh-test-module (SshClientFactory. factory-fn)))
