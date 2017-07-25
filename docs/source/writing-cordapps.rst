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
