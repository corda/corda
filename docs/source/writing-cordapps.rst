Writing a CorDapp
=================

When writing a CorDapp, you are writing a set of files in a JVM language that defines one or more of the following
Corda components:

* States (i.e. classes implementing ``ContractState``)
* Contracts (i.e. classes implementing ``Contract``)
* Flows (i.e. classes extending ``FlowLogic``)
* Web APIs
* Services

CorDapp structure
-----------------
Your CorDapp project's structure should be based on the structure of the
`Java Template CorDapp <https://github.com/corda/cordapp-template-java>`_ or the
`Kotlin Template CorDapp <https://github.com/corda/cordapp-template-kotlin>`_, depending on which language you intend
to use.

The ``src`` directory of the Template CorDapp, where we define our CorDapp's source-code, has the following structure:

.. parsed-literal::

    src
    ├── main
    │   ├── java
    │   │   └── com
    │   │       └── template
    │   │           ├── Main.java
    │   │           ├── api
    │   │           │   └── TemplateApi.java
    │   │           ├── client
    │   │           │   └── TemplateClientRPC.java
    │   │           ├── contract
    │   │           │   └── TemplateContract.java
    │   │           ├── flow
    │   │           │   └── TemplateFlow.java
    │   │           ├── plugin
    │   │           │   └── TemplatePlugin.java
    │   │           ├── service
    │   │           │   └── TemplateService.java
    │   │           └── state
    │   │               └── TemplateState.java
    │   └── resources
    │       ├── META-INF
    │       │   └── services
    │       │       ├── net.corda.core.node.CordaPluginRegistry
    │       │       └── net.corda.webserver.services.WebServerPluginRegistry
    │       ├── certificates
    │       │   ├── sslkeystore.jks
    │       │   └── truststore.jks
    │       └──templateWeb
    │           ├── index.html
    │           └── js
    │               └── template-js.js
    └── test
        └── java
            └── com
                └── template
                    └── contract
                        └── TemplateTests.java

The build file
--------------
At the root of the Template CorDapp, you will also find a ``build.gradle`` file. This file is useful for several
reasons:

Choosing your CorDapp version
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The following two lines of the ``build.gradle`` file define the Corda version used to build your CorDapp:

.. sourcecode:: groovy

    ext.corda_release_version = '0.13.0'
    ext.corda_gradle_plugins_version = '0.13.3'

In this case, our CorDapp will use the Milestone 13 release of Corda, and version 13.3 of the Corda gradle plugins. You
can find the latest published version of both here: https://bintray.com/r3/corda.

``corda_gradle_plugins_versions`` are given in the form ``major.minor.patch``. You should use the same ``major`` and
``minor`` versions as the Corda version you are using, and the latest ``patch`` version. A list of all the available
versions can be found here: https://bintray.com/r3/corda/cordformation.

In certain cases, you may also wish to build against the unstable Master branch. See :doc:`building-against-master`.

Project dependencies
^^^^^^^^^^^^^^^^^^^^
If your CorDapps have any additional external dependencies, they should be added to the ``dependencies`` section:

.. sourcecode:: groovy

   dependencies {

       ...

       // Cordapp dependencies
       // Specify your cordapp's dependencies below, including dependent cordapps
   }

For further information about managing dependencies, see
`the Gradle docs <https://docs.gradle.org/current/userguide/dependency_management.html>`_.

Build tasks
^^^^^^^^^^^
The build file also defines a number of build tasks that will allow us to package up our plugin. We will discuss these
later.

Defining plugins
----------------
Your CorDapp may need to define two types of plugins:

* ``CordaPluginRegistry`` subclasses, which define additional serializable classes and vault schemas
* ``WebServerPluginRegistry`` subclasses, which define the APIs and static web content served by your CorDapp

The fully-qualified class path of each ``CordaPluginRegistry`` subclass must then be added to the
``net.corda.core.node.CordaPluginRegistry`` file in the CorDapp's ``resources/META-INF/services`` folder. Meanwhile,
the fully-qualified class path of each ``WebServerPluginRegistry`` subclass must be added to the
``net.corda.webserver.services.WebServerPluginRegistry`` file, again in the CorDapp's ``resources/META-INF/services``
folder.

The ``CordaPluginRegistry`` class defines the following:

* ``customizeSerialization``, which can be overridden to provide a list of the classes to be whitelisted for object
  serialisation, over and above those tagged with the ``@CordaSerializable`` annotation. See :doc:`serialization`

* ``requiredSchemas``, which can be overridden to return a set of the MappedSchemas to use for persistence and vault
  queries

The ``WebServerPluginRegistry`` class defines the following:

* ``webApis``, which can be overridden to return a list of JAX-RS annotated REST access classes. These classes will be
  constructed by the bundled web server and must have a single argument constructor taking a ``CordaRPCOps`` object.
  This will allow the API to communicate with the node process via the RPC interface. These web APIs will not be
  available if the bundled web server is not started

* ``staticServeDirs``, which can be overridden to map static web content to virtual paths and allow simple web demos to
  be distributed within the CorDapp jars. This static content will not be available if the bundled web server is not
  started

  * The static web content itself should be placed inside the ``src/main/resources`` directory

Building your CorDapp
---------------------
You build a CorDapp by running the gradle ``jar`` task to package up the CorDapp's source files into a jar file.

By default, the jar will be created under ``build/libs``.

Installing CorDapps
-------------------
Once you've built your CorDapp jar, you install it on a node by adding it to the node's ``<node_dir>/plugins/``
folder (where ``node_dir`` is the folder in which the node's JAR and configuration files are stored).

At runtime, nodes will load any plugins present in their ``plugins`` folder.

You can also create a set of nodes with any CorDapps defined in your source folder already installed by running the
Cordapp Template's ``deployNodes`` task. See :doc:`deploying-a-node`.