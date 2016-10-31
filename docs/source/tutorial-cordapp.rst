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

``git checkout -b cordapp-m4 origin/M4``

As with the r3prototyping repository, you can also run from master if you wish to have access to new features but
potentially sacrifice stability.

.. warning:: Make sure that you check-out the correct version of the CorDapp template. E.g. if you are working with
  Corda core M4 then use the M4 version of the CorDapp template.

We recommend you develop your CorDapp with IntelliJ. Boot up IntelliJ. Navigate to ``File > Open ...``. Select the
folder which you cloned the cordapp-template repository to. When IntelliJ advises you that your Gradle project is
unlinked (via a little bubble which pops up), click on ``import Gradle project``.

IntelliJ will resolve all the Corda dependencies along with sources and JavaDocs. You are now good to start building
your first CorDapp!

CorDapp-template Project Structure
----------------------------------

The CorDapp template contains comprises of the following directory structure:

.. sourcecode:: bash

    . cordapp-template
    ├── README.md
    ├── build.gradle
    ├── config
    │   ├── ...
    ├── gradle
    │   └── ...
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── lib
    │   ├── ...
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

In the file structure above, there are a number of auxillary files and folders you don't need to pay too much attention
to:

* The **root directory** contains some gradle files and a README.
* **config** contains log4j configs.
* **gradle** contains the gradle wrapper, which allows the use of Gradle without installing it yourself and worrying
  about which version is required.
* **lib** contains the Quasar.jar which is required for runtime instrumentation of classes by Quasar.

The other parts are of greater importance and covered below.

The build.gradle File
---------------------

It is usually necessary to make a couple of changes to the **build.gradle** file.

**The buildscript**

The buildscript is always located at the top of the file. It specifies version numbers for dependencies, among other
things. Ensure that ``corda_version`` is the same as the Corda core modules you published to Maven local. If not then
``git checkout`` the correct version of the cordapp-template.

.. sourcecode:: groovy

  buildscript {
      ext.kotlin_version = '1.0.4'
      ext.corda_version = '0.5-SNAPSHOT' // Ensure this version is the same as the corda core modules you are using.
      ext.quasar_version = '0.7.6'
      ext.jersey_version = '2.23.1'

      repositories {
        ...
      }

      dependencies {
        ...
      }
  }

**Project dependencies**

If you have any additional external dependencies for your CorDapp then add them below the comment at the end of this
code snippet.package. Use the format:

``compile "{groupId}:{artifactId}:{versionNumber}"``

.. sourcecode:: groovy

  dependencies {
      compile "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
      testCompile group: 'junit', name: 'junit', version: '4.11'

      // Corda integration dependencies
      compile "com.r3corda:client:$corda_version"
      compile "com.r3corda:core:$corda_version"
      compile "com.r3corda:contracts:$corda_version"
      compile "com.r3corda:node:$corda_version"
      compile "com.r3corda:corda:$corda_version"
      compile "com.r3corda:test-utils:$corda_version"

      ...

      // Cordapp dependencies
      // Specify your cordapp's dependencies below, including dependent cordapps
  }

For further information about managing depdencies with Gradle look `here <https://docs.gradle.org/current/userguide/dependency_management.html>`_.

**CordFormation**

This is the local node deployment system for CorDapps, the nodes generated are intended to be used for experimenting,
debugging, and testing node configurations and setups but not intended for production or testnet deployment.

In the CorDapp build.gradle file you'll find a ``deployNodes`` task, this is where you configure the nodes you would
like to deploy for testing. See further details below:

.. sourcecode:: groovy

  task deployNodes(type: com.r3corda.plugins.Cordform, dependsOn: ['build']) {
      directory "./build/nodes" // The output directory.
      networkMap "Controller" // The artemis address of the node to be used as the network map.
      node {
          name "Controller" // Artemis name of node to be deployed.
          dirName "controller" // Directory to which the node will
          nearestCity "London" // For use with the network visualiser.
          advertisedServices = ["corda.notary.validating"] // A list of services you wish the node to offer.
          artemisPort 12345
          webPort 12346 // Usually 1 higher than the Artemis port.
          cordapps = [] // Add package names of CordaApps.
      }
      node {
          name "NodeA"
          dirName "nodea"
          nearestCity "London"
          advertisedServices = []
          artemisPort 31337
          webPort 31339
          cordapps = []
      }
      ...
  }

You can add any number of nodes, with any number of services / CorDapps by editing the templates in ``deployNodes``. The
only requirement is that you must specify a node to run as the network map service and one as the notary service.

.. note:: CorDapps in the current cordapp-template project are automatically registered with all nodes defined in
  ``deployNodes``, although we expect this to change in the near future.

.. warning:: Make sure that there are no port clashes!

Service Provider Configuration File
-----------------------------------

some chat about resources/META-INF/com.r3corda.core.node.CordaPluginRegistry.

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

You sub-class it like this:

.. sourcecode:: kotlin

  class Plugin() : CordaPluginRegistry() {
    ... to be completed ...
  }

**Static Served Content**

Some chat about serving static content. E.g. from resources/exampleWeb.

**Protocols**

To be completed.

**Services**

Take an instance of ``ServicehubInternal``, which gives you access to a whole bunch of stuff. To be completed.

The CorDapp Skeleton
--------------------

* MainKt
* api
* client
* contract
* model
* plugin
* protocol

**API**

.. sourcecode:: kotlin

  // API is accessible from /api/example. All paths specified below are relative to it.
  @Path("example")
  class ExampleApi(val services: ServiceHub) {

      ...

      /**
       * Displays all current example deals in the ledger
       */
      @GET
      @Path("deals")
      @Produces(MediaType.APPLICATION_JSON)
      fun getDeals(): Any {
          val states = services.vaultService.linearHeadsOfType<ExampleState>()
          return states
      }

      /**
       * This initiates a protocol to agree a deal with the other party. Once the protocol finishes it will
       * have written this deal to the ledger.
       */
      @PUT
      @Path("{party}/create-deal")
      fun createDeal(swap: ExampleModel, @PathParam("party") partyName: String): Response {
          val otherParty = services.identityService.partyFromName(partyName)
          if(otherParty != null) {
              // The line below blocks and waits for the future to resolve.
              services.invokeProtocolAsync<ExampleState>(ExampleProtocol.Requester::class.java, swap, otherParty).get()
              return Response.status(Response.Status.CREATED).build()
          } else {
              return Response.status(Response.Status.BAD_REQUEST).build()
          }
      }
  }

**Client**

Some chat about the client RPC framework.

**Contract**

Stuff to go here.

**Model**

.. sourcecode:: kotlin

  /**
   * A simple class with arbitrary data to be written to the ledger. In reality this could be a representation
   * of some kind of trade such as an IRS swap for example.
   */
  data class ExampleModel(val swapRef: String, val data: String)

**Protocols**

Stuff to go here.

Deploying Your Nodes Locally
----------------------------

Some chat about ``./gradlew deployNodes``.

Talk about what is deployed and in what directories.

Node.conf.

/plugins folder.

Starting your nodes
-------------------

**Via the command line**

cd build/nodes
sh runnodes

# All the nodes will startup in the current terminal window.
# Check the deployNodes gradle task to see what port numbers to use.
# You can see that all the nodes offer a web server and api server.

** Via IntelliJ**

Running from intelliJ (via the driver DSL).

Using the cordapp-template project
----------------------------------

* Accessing the static served content.
* Accessing the http API.
* Accessing via the client RPC framework.
* Persistence, etc.
* Defining new node services.
* Defining new protocols.
* defining new contracts.
* definining new states.
* defining new data structures.