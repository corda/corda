.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Using the Corda SDK
===================

This guide covers how to get started with the `cordapp-template`, the Corda SDK.

Installing Corda modules
------------------------

Firstly, follow the :doc:`getting set up <getting-set-up>` page to download the r3prototyping repository, JDK and
IntelliJ.

At the time of writing the r3prototyping repository comprises of the following Gradle projects:

* **buildSrc** contains necessary gradle plugins to build Corda.
* **client** contains the RPC client framework.
* **contracts** defines a range of elementary contracts such as abstract fungible assets, cash,
* **core** containing the core Corda libraries such as crypto functions, types for Corda's building blocks; states,
  contracts, transactions, attachments, etc. and some interfaces for nodes and protocols.
* **experimental** contains a range of things which have not yet been code reviewed.
* **explorer** which is a GUI front-end for Corda.
* **gradle-plugins** contains a series of plugins necessary for building Corda and publishing JARs.
* **node** contains anything specifically required for creating, running and managing nodes (eg: node driver, servlets,
  node services, messaging), persistence
* **test-utils** Defines some helpers for testing such as a DSL for defining contracts as well as a framework for creating
  mock Corda networks.

Once you've cloned the r3prototyping repository check-out the M4 release tag onto a new local branch.

``git checkout -b corda-m4 tags/release-M0.4``

.. note:: You may also opt to work with `origin/master` if you wish to have access to new features but potentially
  sacrafice stability.

Next step is to publish the Corda JARs to your local Maven repository. By default the Maven local repository can be
found:

* ``~/.m2`` on Unix/Mac OS X
* ``C:\Documents and Settings\{your-username}\.m2`` on windows.

Publishing can ber done with running the following Gradle task from the root project directory:

Unix/Mac OSX:

``./gradlew publishToMavenLocal``

Windows:

``gradlew.bat publishToMavenLocal``

This will install all required modules, along with sources and JavaDocs to your local Maven repository.

.. note:: From the open source release date, CorDapp developers will be able to obtain the Corda JARs directly from
  Maven instead of having to clone the r3prototyping repository and publish the Corda JARs to your local Maven
  repository.

As subsequent milestone versions of Corda are released you can pull the new changes from the r3prototyping repository,
check-out the new milestone release and publish the new milestone release to your local Maven repository.

Getting the CorDapp-template SDK
--------------------------------

CorDapps were introduced in Corda milestone M4. They allow developers to arbitrarily extend the functionality of a Corda
node with additional services, protocols and contracts. Typically, you should expect to start all Corda based
development projects using the SDK we provide. It contains the necessary bare-bones for you to begin building your own
CorDapp.

We maintain a separate repository for the Corda-SDK. You can find the repository `here <https://bitbucket.org/R3-CEV/cordapp-template>`_.

You can clone the repository with the following command:

``git clone https://bitbucket.org/R3-CEV/cordapp-template``

One you've cloned the respository, check-out the M4 version as a new local branch:

``git checkout -b corcapp-m4 origin/M4``

As with the r3prototyping repository, you can also run from master if you wish to have access to new features but
potentially sacrifice stability.

.. warning:: Make sure that you check-out the correct version of the CorDapp template. E.g. if you are working with
  Corda core M4 then use the M4 version of the CorDapp template.

We recommend you develop your CorDapp with IntelliJ. Boot up IntelliJ and point it to `open...`

CorDapp-template Project Structure
----------------------------------

The CorDapp template contains comprises of the following directory structure:

.. sourcecode:: shell

    . cordapp-template
    ├── README.md
    ├── build.gradle
    ├── config
    │   ├── dev
    │   │   └── log4j2.xml
    │   └── test
    │       └── log4j2.xml
    ├── gradle
    │   └── wrapper
    │       ├── gradle-wrapper.jar
    │       └── gradle-wrapper.properties
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── lib
    │   ├── README.txt
    │   └── quasar.jar
    ├── settings.gradle
    └── src
        ├── main
        │   ├── java
        │   ├── kotlin
        │   │   └── com
        │   │       └── example
        │   │           ├── Main.kt
        │   │           ├── api
        │   │           │   └── ExampleApi.kt
        │   │           ├── client
        │   │           │   └── ExampleClientRPC.kt
        │   │           ├── contract
        │   │           │   ├── ExampleContract.kt
        │   │           │   └── ExampleState.kt
        │   │           ├── model
        │   │           │   └── ExampleModel.kt
        │   │           ├── plugin
        │   │           │   └── ExamplePlugin.kt
        │   │           └── protocol
        │   │               └── ExampleProtocol.kt
        │   └── resources
        │       ├── META-INF
        │       │   └── services
        │       │       └── com.r3corda.core.node.CordaPluginRegistry
        │       ├── certificates
        │       │   ├── readme.txt
        │       │   ├── sslkeystore.jks
        │       │   └── truststore.jks
        │       └── exampleWeb
        │           ├── index.html
        │           └── js
        │               └── example.js
        └── test
            ├── java
            ├── kotlin
            │   └── com
            │       └── example
            │           └── ExampleTest.kt
            └── resources

* The **root directory** contains some gradle files and a README.
* **config** contains necessary gradle plugins to build Corda.
* **buildSrc** contains necessary gradle plugins to build Corda.
* **buildSrc** contains necessary gradle plugins to build Corda.
* **buildSrc** contains necessary gradle plugins to build Corda.

The cordapp-template SDK includes the framework for a basic CorDapp setup.

All CorDapps must sub-class the CordaPlugin Registry class.

.. sourcecode:: kotlin

  /**
   * Implement this interface on a class advertised in a META-INF/services/com.r3corda.core.node.CordaPluginRegistry file
   * to extend a Corda node with additional application services.
   */
  abstract class CordaPluginRegistry {
      /**
       * List of JAX-RS classes inside the contract jar. They are expected to have a single parameter constructor that takes a ServiceHub as input.
       * These are listed as Class<*>, because in the future they will be instantiated inside a ClassLoader so that
       * Cordapp code can be loaded dynamically.
       */
      open val webApis: List<Class<*>> = emptyList()

      /**
       * Map of static serving endpoints to the matching resource directory. All endpoints will be prefixed with "/web" and postfixed with "\*.
       * Resource directories can be either on disk directories (especially when debugging) in the form "a/b/c". Serving from a JAR can
       *  be specified with: javaClass.getResource("<folder-in-jar>").toExternalForm()
       */
      open val staticServeDirs: Map<String, String> = emptyMap()

      /**
       * A Map with an entry for each consumed protocol used by the webAPIs.
       * The key of each map entry should contain the ProtocolLogic<T> class name.
       * The associated map values are the union of all concrete class names passed to the protocol constructor.
       * Standard java.lang.* and kotlin.* types do not need to be included explicitly.
       * This is used to extend the white listed protocols that can be initiated from the ServiceHub invokeProtocolAsync method.
       */
      open val requiredProtocols: Map<String, Set<String>> = emptyMap()

      /**
       * List of additional long lived services to be hosted within the node.
       * They are expected to have a single parameter constructor that takes a ServiceHubInternal as input.
       * The ServiceHubInternal will be fully constructed before the plugin service is created and will
       * allow access to the protocol factory and protocol initiation entry points there.
       */
      open val servicePlugins: List<Class<*>> = emptyList()
  }

# Edit the deployNodes gradle task as required.
# Can add or remove nodes.

./gradlew deployNodes

cd build/nodes
sh runnodes

# All the nodes will startup in the current terminal window.
# Check the deployNodes gradle task to see what port numbers to use.
# You can see that all the nodes offer a web server and api server.

Build.gradle
------------

* corda version. Needs to match that of the corda core version you are using.
* understanding the build gradle file. deploy nodes specifically. How to deploy different classes of node. Deploy nodes is
  used to run small test networks of nodes on your local machine. you need a network map service and a notary at a minimum.
* node.conf
* running the nodes.
* Accessing the static served content.
* Accessing the http API.
* Defining new node services.
* Defining new protocols.
* defining new contracts.
* definining new states.
* defining new data structures.
* running from intelliJ (the driver DSL).


