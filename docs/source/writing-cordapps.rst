Writing a CorDapp
=================

The source-code for a CorDapp is a set of files written in a JVM language that defines a set of Corda components:

* States (i.e. classes implementing ``ContractState``)
* Contracts (i.e. classes implementing ``Contract``)
* Flows (i.e. classes extending ``FlowLogic``)
* Web APIs
* Services

These files should be placed under ``src/main/[java|kotlin]``. The CorDapp's resources folder (``src/main/resources``)
should also include the following subfolders:

* ``src/main/resources/certificates``, containing the node's certificates
* ``src/main/resources/META-INF/services``, containing a file named ``net.corda.core.node.CordaPluginRegistry``

For example, the source-code of the `Template CorDapp <https://github.com/corda/cordapp-template>`_ has the following
structure:

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
    │       │       └── net.corda.core.node.CordaPluginRegistry
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

Defining a plugin
-----------------
You can specify the web APIs and static web content for your CorDapp by subclassing
``net.corda.core.node.CordaPluginRegistry``:

* The ``webApis`` property is a list of JAX-RS annotated REST access classes. These classes will be constructed by
  the bundled web server and must have a single argument constructor taking a ``CordaRPCOps`` object. This will
  allow the API to communicate with the node process via the RPC interface. These web APIs will not be available if the
  bundled web server is not started.

* The ``staticServeDirs`` property maps static web content to virtual paths and allows simple web demos to be
  distributed within the CorDapp jars. These static serving directories will not be available if the bundled web server
  is not started.
  * The static web content itself should be placed inside the ``src/main/resources`` directory

* The ``customizeSerialization`` function allows classes to be whitelisted for object serialisation, over and
  above those tagged with the ``@CordaSerializable`` annotation. For instance, new state types will need to be
  explicitly registered. In general, the annotation should be preferred. See :doc:`serialization`.

The fully-qualified class path of each ``CordaPluginRegistry`` subclass must be added to the
``net.corda.core.node.CordaPluginRegistry`` file in the CorDapp's ``resources/META-INF/services`` folder. A CorDapp
can register multiple plugins in a single ``net.corda.core.node.CordaPluginRegistry`` file.

Installing CorDapps
-------------------
To run a CorDapp, its source is compiled into a JAR by running the gradle ``jar`` task. The CorDapp JAR is then added
to a node by adding it to the node's ``<node_dir>/plugins/`` folder (where ``node_dir`` is the folder in which the
node's JAR and configuration files are stored).

.. note:: Any external dependencies of your CorDapp will automatically be placed into the
   ``<node_dir>/dependencies/`` folder. This will be changed in a future release.

.. note:: Building nodes using the gradle ``deployNodes`` task will place the CorDapp JAR into each node's ``plugins``
   folder automatically.

At runtime, nodes will load any plugins present in their ``plugins`` folder.

RPC permissions
---------------
If a node's owner needs to interact with their node via RPC (e.g. to read the contents of the node's storage), they
must define one or more RPC users. These users are added to the node's ``node.conf`` file.

The syntax for adding an RPC user is:

.. container:: codeset

    .. sourcecode:: groovy

        rpcUsers=[
            {
                username=exampleUser
                password=examplePass
                permissions=[]
            }
            ...
        ]

Currently, users need special permissions to start flows via RPC. These permissions are added as follows:

.. container:: codeset

    .. sourcecode:: groovy

        rpcUsers=[
            {
                username=exampleUser
                password=examplePass
                permissions=[
                    "StartFlow.net.corda.flows.ExampleFlow1",
                    "StartFlow.net.corda.flows.ExampleFlow2"
                ]
            }
            ...
        ]

.. note:: Currently, the node's web server has super-user access, meaning that it can run any RPC operation without
   logging in. This will be changed in a future release.
