# pallet-jclouds

[![Build Status](https://travis-ci.org/pallet/pallet-jclouds.png?branch=develop)](https://travis-ci.org/pallet/pallet-jclouds)

A provider for [Pallet][palletops], to use [jclouds][jclouds] to access many clouds.

## Pallet

[Pallet][palletops] is used to provision and maintain servers on cloud and
virtual machine infrastructure, and aims to solve the problem of providing a
consistently configured running image across a range of clouds.  It is designed
for use from the [Clojure][clojure] REPL, from clojure code, and from the
command line.

- reuse configuration in development, testing and production.
- store all your configuration in a source code management system (eg. git),
  including role assignments.
- configuration is re-used by compostion; just create new functions that call
  existing crates with new arguments. No copy and modify required.
- enable use of configuration crates (recipes) from versioned jar files.

[Documentation][docs] is available.

## Installation

Pallet-jclouds is distributed as a jar, and is available in the
[sonatype repository][sonatype].

Installation is with maven or your favourite maven repository aware build tool.

### Latest versions

jclouds 1.2.x
: pallet-jclouds-1.2.0-alpha1

jclouds 1.3.x
: pallet-jclouds-1.3.0

jclouds 1.4.x
: pallet-jclouds-1.4.2

jclouds 1.5.x
: pallet-jclouds-1.5.4 (jclouds 1.5.5)

jclouds 1.7.x
: pallet-jclouds-1.7.0-alpha.2 (jclouds 1.7.1)

### lein project.clj

```clojure
:dependencies [[com.palletops/pallet "0.8.0-RC.8"]
               [com.palletops/pallet-jclouds "1.7.0-alpha.2"]]
```

### maven pom.xml

```xml
<dependencies>
  <dependency>
    <groupId>com.palletops</groupId>
    <artifactId>pallet</artifactId>
    <version>0.8.0-RC.8</version>
  </dependency>
  <dependency>
    <groupId>com.palletops</groupId>
    <artifactId>pallet-jclouds</artifactId>
    <version>1.7.0-alpha.2</version>
  </dependency>
<dependencies>

<repositories>
  <repository>
    <id>clojars</id>
    <url>http://clojars.org/repo</url>
  </repository>
</repositories>
```

## Using other jclouds versions

To uses a different minor version, say 1.7.0, you can use the nearest matching
pallet-jclouds version, add exclusions for the default jclouds version in that
release, and add jclouds dependencies for the jclouds version you would like.

## License

Licensed under [EPL](http://www.eclipse.org/legal/epl-v10.html)

[Contributors](https://www.ohloh.net/p/pallet-clj/contributors)

Copyright 2010, 2011, 2012, 2013 Hugo Duncan.


[palletops]: http://palletops.com "Pallet site"

[docs]: http://palletops.com/doc "Pallet Documentation"
[ml]: http://groups.google.com/group/pallet-clj "Pallet mailing list"
[basicdemo]: https://github.com/pallet/pallet-examples/blob/develop/basic/src/demo.clj "Basic interactive usage of Pallet"
[basic]: https://github.com/pallet/pallet-examples/tree/develop/basic/ "Basic Pallet Examples"
[screencast]: http://www.youtube.com/hugoduncan#p/u/1/adzMkR0d0Uk "Pallet Screencast"
[clojure]: http://clojure.org "Clojure"
[cljstart]: http://dev.clojure.org/display/doc/Getting+Started "Getting started with clojure"
[sonatype]: http://oss.sonatype.org/content/repositories/releases/org/cloudhoist "Sonatype Maven Repository"

[jclouds]: http://jclouds.org/ "jclouds"
[chef]: http://opscode.com/ "Chef"
[puppet]: http://www.puppetlabs.com/ "Puppet"
