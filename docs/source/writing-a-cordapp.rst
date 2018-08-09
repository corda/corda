CorDapp structure
=================

.. contents::

.. _cordapp-structure:

Template CorDapps
-----------------
You should base your project on one of the following templates:

* `Java Template CorDapp <https://github.com/corda/cordapp-template-java>`_ (for CorDapps written in Java)
* `Kotlin Template CorDapp <https://github.com/corda/cordapp-template-kotlin>`_ (for CorDapps written in Kotlin)

Please use the branch of the template that corresponds to the major version of Corda you are using. For example,
someone building a CorDapp on Corda 3.2 should use the ``release-V3`` branch of the template.

Build system
------------
The template is set up to be built using Gradle. A Gradle wrapper is provided in the ``wrapper`` folder, and the
dependencies are defined in the ``build.gradle`` files. See :doc:`cordapp-build-systems` for more information.

Structure
---------
The templates are split into two modules:

* A ``cordapp-contracts-states`` module containing classes such as contracts and states that will be sent across the
  wire as part of a flow
* A ``cordapp`` module containing the remaining classes

Each module will be compiled into its own CorDapp. This minimises the size of the JAR that has to be sent across the
wire when nodes are agreeing ledger updates.

Module one - cordapp-contracts-states
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Here is the structure of the ``src`` directory for the ``cordapp-contracts-states`` module of the Java template:

.. parsed-literal::

    .
    └── main
        └── java
            └── com
                └── template
                    ├── TemplateContract.java
                    └── TemplateState.java

The directory only contains two class definitions:

* ``TemplateContract``
* ``TemplateState``

These are definitions for classes that we expect to have to send over the wire. They will be compiled into their own
CorDapp.

Module two - cordapp
^^^^^^^^^^^^^^^^^^^^
Here is the structure of the ``src`` directory for the ``cordapp`` module of the Java template:

.. parsed-literal::

    .
    ├── main
    │   ├── java
    │   │   └── com
    │   │       └── template
    │   │           ├── TemplateApi.java
    │   │           ├── TemplateClient.java
    │   │           ├── TemplateFlow.java
    │   │           ├── TemplateSerializationWhitelist.java
    │   │           └── TemplateWebPlugin.java
    │   └── resources
    │       ├── META-INF
    │       │   └── services
    │       │       ├── net.corda.core.serialization.SerializationWhitelist
    │       │       └── net.corda.webserver.services.WebServerPluginRegistry
    │       ├── certificates
    │       └── templateWeb
    ├── test
    │   └── java
    │       └── com
    │           └── template
    │               ├── ContractTests.java
    │               ├── FlowTests.java
    │               └── NodeDriver.java
    └── integrationTest
        └── java
            └── com
                └── template
                    └── DriverBasedTest.java

The ``src`` directory is structured as follows:

* ``main`` contains the source of the CorDapp
* ``test`` contains example unit tests, as well as a node driver for running the CorDapp from IntelliJ
* ``integrationTest`` contains an example integration test

Within ``main``, we have the following directories:

* ``java``, which contains the source-code for our CorDapp:

    * ``TemplateFlow.java``, which contains a template ``FlowLogic`` subclass
    * ``TemplateState.java``, which contains a template ``ContractState`` implementation
    * ``TemplateContract.java``, which contains a template ``Contract`` implementation
    * ``TemplateSerializationWhitelist.java``, which contains a template ``SerializationWhitelist`` implementation
    * ``TemplateApi.java``, which contains a template API for the deprecated Corda webserver
    * ``TemplateWebPlugin.java``, which registers the API and front-end for the deprecated Corda webserver
    * ``TemplateClient.java``, which contains a template RPC client for interacting with our CorDapp

* ``resources/META-INF/services``, which contains various registries:

    * ``net.corda.core.serialization.SerializationWhitelist``, which registers the CorDapp's serialisation whitelists
    * ``net.corda.webserver.services.WebServerPluginRegistry``, which registers the CorDapp's web plugins

* ``resources/templateWeb``, which contains a template front-end

In a production CorDapp:

* We would remove the files related to the deprecated Corda webserver (``TemplateApi.java``,
  ``TemplateWebPlugin.java``, ``resources/templateWeb``, and ``net.corda.webserver.services.WebServerPluginRegistry``)
  and replace them with a production-ready webserver

* We would also move ``TemplateClient.java`` into a separate module so that it is not included in the CorDapp