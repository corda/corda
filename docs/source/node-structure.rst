Node structure
==============

.. contents::

A Corda node has the following structure:

.. sourcecode:: none

    .
    ├── additional-node-infos   // Additional node infos to load into the network map cache, beyond what the network map server provides
    ├── artemis                 // Stores buffered P2P messages
    ├── brokers                 // Stores buffered RPC messages
    ├── certificates            // The node's certificates
    ├── corda-webserver.jar     // The built-in node webserver
    ├── corda.jar               // The core Corda libraries
    ├── cordapps                // The CorDapp JARs installed on the node
    ├── drivers                 // Contains a Jolokia driver used to export JMX metrics
    ├── logs                    // The node logs
    ├── network-parameters      // The network parameters automatically downloaded from the network map server
    ├── node.conf               // The node's configuration files
    ├── persistence.mv.db       // The node's database
    └── shell-commands          // Custom shell commands defined by the node owner

The node is configured by editing its ``node.conf`` file (see :doc:`corda-configuration-file`). You install CorDapps on
the node by dropping CorDapp JARs into the ``cordapps`` folder.

In development mode (i.e. when ``devMode = true``, see :doc:`corda-configuration-file`), the ``certificates``
directory is filled with pre-configured keystores if the required keystores do not exist. This ensures that developers
can get the nodes working as quickly as possible. However, these pre-configured keystores are not secure, to learn more
see :doc:`permissioning`.

.. _node_naming:

Node naming
-----------
A node's name must be a valid X.500 distinguished name. In order to be compatible with other implementations
(particularly TLS implementations), we constrain the allowed X.500 name attribute types to a subset of the minimum
supported set for X.509 certificates (specified in RFC 3280), plus the locality attribute:

* Organization (O)
* State (ST)
* Locality (L)
* Country (C)
* Organizational-unit (OU)
* Common name (CN)

Note that the serial number is intentionally excluded in order to minimise scope for uncertainty in the distinguished name format.
The distinguished name qualifier has been removed due to technical issues; consideration was given to "Corda" as qualifier,
however the qualifier needs to reflect the compatibility zone, not the technology involved. There may be many Corda namespaces,
but only one R3 namespace on Corda. The ordering of attributes is important.

``State`` should be avoided unless required to differentiate from other ``localities`` with the same or similar names at the
country level. For example, London (GB) would not need a ``state``, but St Ives would (there are two, one in Cornwall, one
in Cambridgeshire). As legal entities in Corda are likely to be located in major cities, this attribute is not expected to be
present in the majority of names, but is an option for the cases which require it.

The name must also obey the following constraints:

* The ``organisation``, ``locality`` and ``country`` attributes are present

    * The ``state``, ``organisational-unit`` and ``common name`` attributes are optional

* The fields of the name have the following maximum character lengths:

    * Common name: 64
    * Organisation: 128
    * Organisation unit: 64
    * Locality: 64
    * State: 64

* The ``country`` attribute is a valid ISO 3166-1 two letter code in upper-case

* All attributes must obey the following constraints:

    * Upper-case first letter
    * Has at least two letters
    * No leading or trailing whitespace
    * Does not include the following characters: ``,`` , ``=`` , ``$`` , ``"`` , ``'`` , ``\``
    * Is in NFKC normalization form
    * Does not contain the null character
    * Only the latin, common and inherited unicode scripts are supported

* The ``organisation`` field of the name also obeys the following constraints:

    * No double-spacing

        * This is to avoid right-to-left issues, debugging issues when we can't pronounce names over the phone, and
          character confusability attacks

External identifiers
^^^^^^^^^^^^^^^^^^^^
Mappings to external identifiers such as Companies House nos., LEI, BIC, etc. should be stored in custom X.509
certificate extensions. These values may change for operational reasons, without the identity they're associated with
necessarily changing, and their inclusion in the distinguished name would cause significant logistical complications.
The OID and format for these extensions will be described in a further specification.
