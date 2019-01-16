Node folder structure
=====================

A folder containing a Corda node files has the following structure:

.. sourcecode:: none

    .
    ├── additional-node-infos   // Additional node infos to load into the network map cache, beyond what the network map server provides
    ├── artemis                 // Stores buffered P2P messages
    ├── brokers                 // Stores buffered RPC messages
    ├── certificates            // The node's certificates
    ├── corda-webserver.jar     // The built-in node webserver (DEPRECATED)
    ├── corda.jar               // The core Corda libraries (This is the actual Corda node implementation)
    ├── cordapps                // The CorDapp JARs installed on the node
    ├── drivers                 // Contains a Jolokia driver used to export JMX metrics, the node loads any additional JAR files from this directory at startup.
    ├── logs                    // The node's logs
    ├── network-parameters      // The network parameters automatically downloaded from the network map server
    ├── node.conf               // The node's configuration files
    ├── persistence.mv.db       // The node's database
    └── shell-commands          // Custom shell commands defined by the node owner

You install CorDapps on the node by placing CorDapp JARs in the ``cordapps`` folder.

In development mode (i.e. when ``devMode = true``), the ``certificates`` directory is filled with pre-configured
keystores if they do not already exist to ensure that developers can get the nodes working as quickly as
possible.

.. warning:: These pre-configured keystores are not secure and must not used in a production environments.

The keystores store the key pairs and certificates under the following aliases:

* ``nodekeystore.jks`` uses the aliases ``cordaclientca`` and ``identity-private-key``
* ``sslkeystore.jks`` uses the alias ``cordaclienttls``

All the keystores use the password provided in the node's configuration file using the ``keyStorePassword`` attribute.
If no password is configured, it defaults to ``cordacadevpass``.

To learn more, see :doc:`permissioning`.

