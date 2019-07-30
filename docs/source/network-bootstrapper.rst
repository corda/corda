Network Bootstrapper
====================

Test deployments
~~~~~~~~~~~~~~~~

Nodes within a network see each other using the network map. This is a collection of statically signed node-info files,
one for each node. Most production deployments will use a highly available, secure distribution of the network map via HTTP.

For test deployments where the nodes (at least initially) reside on the same filesystem, these node-info files can be
placed directly in the node's ``additional-node-infos`` directory from where the node will pick them up and store them
in its local network map cache. The node generates its own node-info file on startup.

In addition to the network map, all the nodes must also use the same set of network parameters. These are a set of constants
which guarantee interoperability between the nodes. The HTTP network map distributes the network parameters which are downloaded
automatically by the nodes. In the absence of this the network parameters must be generated locally.

For these reasons, test deployments can avail themselves of the Network Bootstrapper. This is a tool that scans all the
node configurations from a common directory to generate the network parameters file, which is then copied to all the nodes'
directories. It also copies each node's node-info file to every other node so that they can all be visible to each other.

You can find out more about network maps and network parameters from :doc:`network-map`.

Bootstrapping a test network
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Corda Network Bootstrapper can be downloaded from `here <https://software.r3.com/artifactory/corda-releases/net/corda/corda-tools-network-bootstrapper>`__.

Create a directory containing a node config file, ending in "_node.conf", for each node you want to create. "devMode" must be set to true. Then run the
following command:

.. sourcecode:: bash

    java -jar corda-tools-network-bootstrapper-|corda_version|.jar --dir <nodes-root-dir>

For example running the command on a directory containing these files:

.. sourcecode:: none

    .
    ├── notary_node.conf             // The notary's node.conf file
    ├── partya_node.conf             // Party A's node.conf file
    └── partyb_node.conf             // Party B's node.conf file

will generate directories containing three nodes: ``notary``, ``partya`` and ``partyb``. They will each use the ``corda.jar``
that comes with the Network Bootstrapper. If a different version of Corda is required then simply place that ``corda.jar`` file
alongside the configuration files in the directory.

You can also have the node directories containing their "node.conf" files already laid out. The previous example would be:

.. sourcecode:: none

    .
    ├── notary
    │   └── node.conf
    ├── partya
    │   └── node.conf
    └── partyb
        └── node.conf

Similarly, each node directory may contain its own ``corda.jar``, which the Bootstrapper will use instead.

Providing CorDapps to the Network Bootstrapper
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you would like the Network Bootstrapper to include your CorDapps in each generated node, just place them in the directory
alongside the config files. For example, if your directory has this structure:

.. sourcecode:: none

    .
    ├── notary_node.conf            // The notary's node.conf file
    ├── partya_node.conf            // Party A's node.conf file
    ├── partyb_node.conf            // Party B's node.conf file
    ├── cordapp-a.jar               // A cordapp to be installed on all nodes
    └── cordapp-b.jar               // Another cordapp to be installed on all nodes

The ``cordapp-a.jar`` and ``cordapp-b.jar`` will be installed in each node directory, and any contracts within them will be
added to the Contract Whitelist (see below).

.. _bootstrapper_whitelisting_contracts:

Whitelisting contracts
----------------------

Any CorDapps provided when bootstrapping a network will be scanned for contracts which will be used to create the
*Zone whitelist* (see :doc:`api-contract-constraints`) for the network.

.. note:: If you only wish to whitelist the CorDapps but not copy them to each node then run with the ``--copy-cordapps=No`` option.

The CorDapp JARs will be hashed and scanned for ``Contract`` classes. These contract class implementations will become part
of the whitelisted contracts in the network parameters (see ``NetworkParameters.whitelistedContractImplementations`` :doc:`network-map`).

By default the Bootstrapper will whitelist all the contracts found in the unsigned CorDapp JARs (a JAR file not signed by jarSigner tool).
Whitelisted contracts are checked by `Zone constraints`, while contract classes from signed JARs will be checked by `Signature constraints`.
To prevent certain contracts from unsigned JARs from being whitelisted, add their fully qualified class name in the ``exclude_whitelist.txt``.
These will instead use the more restrictive ``HashAttachmentConstraint``.
To add certain contracts from signed JARs to whitelist, add their fully qualified class name in the ``include_whitelist.txt``.
Refer to :doc:`api-contract-constraints` to understand the implication of different constraint types before adding ``exclude_whitelist.txt`` or ``include_whitelist.txt`` files.

For example:

.. sourcecode:: none

    net.corda.finance.contracts.asset.Cash
    net.corda.finance.contracts.asset.CommercialPaper

Modifying a bootstrapped network
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Network Bootstrapper is provided as a development tool for setting up Corda networks for development and testing.
There is some limited functionality which can be used to make changes to a network, but for anything more complicated consider
using a :doc:`Network Map</network-map>` server.

When running the Network Bootstrapper, each ``node-info`` file needs to be gathered together in one directory. If
the nodes are being run on different machines you need to do the following:

* Copy the node directories from each machine into one directory, on one machine
* Depending on the modification being made (see below for more information), add any new files required to the root directory
* Run the Network Bootstrapper from the root directory
* Copy each individual node's directory back to the original machine

The Network Bootstrapper cannot dynamically update the network if an existing node has changed something in their node-info,
e.g. their P2P address. For this the new node-info file will need to be placed in the other nodes' ``additional-node-infos`` directory.
If the nodes are located on different machines, then a utility such as `rsync <https://en.wikipedia.org/wiki/Rsync>`_ can be used
so that the nodes can share node-infos.

Adding a new node to the network
--------------------------------

Running the Bootstrapper again on the same network will allow a new node to be added and its
node-info distributed to the existing nodes.

As an example, if we have an existing bootstrapped network, with a Notary and PartyA and we want to add a PartyB, we
can use the Network Bootstrapper on the following network structure:

.. sourcecode:: none

    .
    ├── notary                      // existing node directories
    │   ├── node.conf
    │   ├── network-parameters
    │   ├── node-info-notary
    │   └── additional-node-infos
    │       ├── node-info-notary
    │       └── node-info-partya
    ├── partya
    │   ├── node.conf
    │   ├── network-parameters
    │   ├── node-info-partya
    │   └── additional-node-infos
    │       ├── node-info-notary
    │       └── node-info-partya
    └── partyb_node.conf            // the node.conf for the node to be added

Then run the Network Bootstrapper again from the root dir:

.. sourcecode:: bash

    java -jar corda-tools-network-bootstrapper-|corda_version|.jar --dir <nodes-root-dir>

Which will give the following:

.. sourcecode:: none

    .
    ├── notary                      // the contents of the existing nodes (keys, db's etc...) are unchanged
    │   ├── node.conf
    │   ├── network-parameters
    │   ├── node-info-notary
    │   └── additional-node-infos
    │       ├── node-info-notary
    │       ├── node-info-partya
    │       └── node-info-partyb
    ├── partya
    │   ├── node.conf
    │   ├── network-parameters
    │   ├── node-info-partya
    │   └── additional-node-infos
    │       ├── node-info-notary
    │       ├── node-info-partya
    │       └── node-info-partyb
    └── partyb                      // a new node directory is created for PartyB
        ├── node.conf
        ├── network-parameters
        ├── node-info-partyb
        └── additional-node-infos
            ├── node-info-notary
            ├── node-info-partya
            └── node-info-partyb

The Bootstrapper will generate a directory and the ``node-info`` file for PartyB, and will also make sure a copy of each
nodes' ``node-info`` file is in the ``additional-node-info`` directory of every node. Any other files in the existing nodes,
such a generated keys, will be unaffected.

.. note:: The Network Bootstrapper is provided for test deployments and can only generate information for nodes collected on
    the same machine. If a network needs to be updated using the Bootstrapper once deployed, the nodes will need
    collecting back together.

.. _bootstrapper_updating_whitelisted_contracts:

Updating the contract whitelist for bootstrapped networks
---------------------------------------------------------

If the network already has a set of network parameters defined (i.e. the node directories all contain the same network-parameters
file) then the Network Bootstrapper can be used to append contracts from new CorDapps to the current whitelist.
For example, with the following pre-generated network:

.. sourcecode:: none

    .
    ├── notary
    │   ├── node.conf
    │   ├── network-parameters
    │   └── cordapps
    │       └── cordapp-a.jar
    ├── partya
    │   ├── node.conf
    │   ├── network-parameters
    │   └── cordapps
    │       └── cordapp-a.jar
    ├── partyb
    │   ├── node.conf
    │   ├── network-parameters
    │   └── cordapps
    │       └── cordapp-a.jar
    └── cordapp-b.jar               // The new cordapp to add to the existing nodes

Then run the Network Bootstrapper again from the root dir:

.. sourcecode:: bash

    java -jar corda-tools-network-bootstrapper-|corda_version|.jar --dir <nodes-root-dir>

To give the following:

.. sourcecode:: none

    .
    ├── notary
    │   ├── node.conf
    │   ├── network-parameters      // The contracts from cordapp-b are appended to the whitelist in network-parameters
    │   └── cordapps
    │       ├── cordapp-a.jar
    │       └── cordapp-b.jar       // The updated cordapp is placed in the nodes cordapp directory
    ├── partya
    │   ├── node.conf
    │   ├── network-parameters      // The contracts from cordapp-b are appended to the whitelist in network-parameters
    │   └── cordapps
    │       ├── cordapp-a.jar
    │       └── cordapp-b.jar       // The updated cordapp is placed in the nodes cordapp directory
    └── partyb
        ├── node.conf
        ├── network-parameters      // The contracts from cordapp-b are appended to the whitelist in network-parameters
        └── cordapps
            ├── cordapp-a.jar
            └── cordapp-b.jar       // The updated cordapp is placed in the nodes cordapp directory

.. note:: The whitelist can only ever be appended to. Once added a contract implementation can never be removed.

Modifying the network parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Network Bootstrapper creates a network parameters file when bootstrapping a network, using a set of sensible defaults. However, if you would like
to override these defaults when testing, there are two ways of doing this. Options can be overridden via the command line or by supplying a configuration
file. If the same parameter is overridden both by a command line argument and in the configuration file, the command line value
will take precedence.

Overriding network parameters via command line
----------------------------------------------

The ``--minimum-platform-version``, ``--max-message-size``, ``--max-transaction-size`` and ``--event-horizon`` command line parameters can
be used to override the default network parameters. See `Command line options`_ for more information.

Overriding network parameters via a file
----------------------------------------

You can provide a network parameters overrides file using the following syntax:

.. sourcecode:: bash

    java -jar corda-tools-network-bootstrapper-|corda_version|.jar --network-parameter-overrides=<path_to_file>

Or alternatively, by using the short form version:

.. sourcecode:: bash

    java -jar corda-tools-network-bootstrapper-|corda_version|.jar -n=<path_to_file>

The network parameter overrides file is a HOCON file with the following fields, all of which are optional. Any field that is not provided will be
ignored. If a field is not provided and you are bootstrapping a new network, a sensible default value will be used. If a field is not provided and you
are updating an existing network, the value in the existing network parameters file will be used.

.. note:: All fields can be used with placeholders for environment variables. For example: ``${KEY_STORE_PASSWORD}`` would be replaced by the contents of environment
    variable ``KEY_STORE_PASSWORD``. See: :ref:`corda-configuration-hiding-sensitive-data` .

The available configuration fields are listed below:

:minimumPlatformVersion: The minimum supported version of the Corda platform that is required for nodes in the network.

:maxMessageSize: The maximum permitted message size, in bytes. This is currently ignored but will be used in a future release.

:maxTransactionSize: The maximum permitted transaction size, in bytes.

:eventHorizon: The time after which nodes will be removed from the network map if they have not been seen during this period. This parameter uses
    the ``parse`` function on the ``java.time.Duration`` class to interpret the data. See `here <https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence->`__
    for information on valid inputs.

:packageOwnership: A list of package owners. See `Package namespace ownership`_ for more information. For each package owner, the following fields
    are required:

    :packageName: Java package name (e.g `com.my_company` ).

    :keystore: The path of the keystore file containing the signed certificate.

    :keystorePassword: The password for the given keystore (not to be confused with the key password).

    :keystoreAlias: The alias for the name associated with the certificate to be associated with the package namespace.

An example configuration file:

.. parsed-literal::

    minimumPlatformVersion=4
    maxMessageSize=10485760
    maxTransactionSize=524288000
    eventHorizon="30 days"
    packageOwnership=[
        {
            packageName="com.example"
            keystore="myteststore"
            keystorePassword="MyStorePassword"
            keystoreAlias="MyKeyAlias"
        }
    ]

.. _package_namespace_ownership:

Package namespace ownership
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Package namespace ownership is a Corda security feature that allows a compatibility zone to give ownership of parts of the Java package
namespace to registered users (e.g. a CorDapp development organisation). The exact mechanism used to claim a namespace is up to the zone
operator. A typical approach would be to accept an SSL certificate with the domain in it as proof of domain ownership, or to accept an email from that domain.

.. note:: Read more about *Package ownership* :doc:`here<design/data-model-upgrades/package-namespace-ownership>`.

A Java package namespace is case insensitive and cannot be a sub-package of an existing registered namespace.
See `Naming a Package <https://docs.oracle.com/javase/tutorial/java/package/namingpkgs.html>`_ and `Naming Conventions <https://www.oracle.com/technetwork/java/javase/documentation/codeconventions-135099.html#28840 for guidelines and conventions>`_ for guidelines on naming conventions.

The registration of a Java package namespace requires the creation of a signed certificate as generated by the
`Java keytool <https://docs.oracle.com/javase/8/docs/technotes/tools/windows/keytool.html>`_.

The packages can be registered by supplying a network parameters override config file via the command line, using the ``--network-parameter-overrides`` command.

For each package to be registered, the following are required:

:packageName: Java package name (e.g `com.my_company` ).

:keystore: The path of the keystore file containing the signed certificate. If a relative path is provided, it is assumed to be relative to the
    location of the configuration file.

:keystorePassword: The password for the given keystore (not to be confused with the key password).

:keystoreAlias: The alias for the name associated with the certificate to be associated with the package namespace.

Using the `Example CorDapp <https://github.com/corda/cordapp-example>`_ as an example, we will initialise a simple network and then register and unregister a package namespace.
Checkout the Example CorDapp and follow the instructions to build it `here <https://docs.corda.net/tutorial-cordapp.html#building-the-example-cordapp>`__.

.. note:: You can point to any existing bootstrapped corda network (this will have the effect of updating the associated network parameters file).

#. Create a new public key to use for signing the Java package namespace we wish to register:

    .. code-block:: shell

        $JAVA_HOME/bin/keytool -genkeypair -keystore _teststore -storepass MyStorePassword -keyalg RSA -alias MyKeyAlias -keypass MyKeyPassword -dname "O=Alice Corp, L=Madrid, C=ES"

    This will generate a key store file called ``_teststore`` in the current directory.

#. Create a ``network-parameters.conf`` file in the same directory, with the following information:

    .. parsed-literal::

        packageOwnership=[
            {
                packageName="com.example"
                keystore="_teststore"
                keystorePassword="MyStorePassword"
                keystoreAlias="MyKeyAlias"
            }
        ]

#. Register the package namespace to be claimed by the public key generated above:

    .. code-block:: shell

        # Register the Java package namespace using the Network Bootstrapper
        java -jar network-bootstrapper.jar --dir build/nodes --network-parameter-overrides=network-parameters.conf


#. To unregister the package namespace, edit the ``network-parameters.conf`` file to remove the package:

    .. parsed-literal::

        packageOwnership=[]

#. Unregister the package namespace:

    .. code-block:: shell

        # Unregister the Java package namespace using the Network Bootstrapper
        java -jar network-bootstrapper.jar --dir build/nodes --network-parameter-overrides=network-parameters.conf

Command line options
~~~~~~~~~~~~~~~~~~~~

The Network Bootstrapper can be started with the following command line options:

.. code-block:: shell

    bootstrapper [-hvV] [--copy-cordapps=<copyCordapps>] [--dir=<dir>]
             [--event-horizon=<eventHorizon>] [--logging-level=<loggingLevel>]
             [--max-message-size=<maxMessageSize>]
             [--max-transaction-size=<maxTransactionSize>]
             [--minimum-platform-version=<minimumPlatformVersion>]
             [-n=<networkParametersFile>] [COMMAND]

* ``--dir=<dir>``: Root directory containing the node configuration files and CorDapp JARs that will form the test network.
  It may also contain existing node directories. Defaults to the current directory.
* ``--copy-cordapps=<copyCordapps>``: Whether or not to copy the CorDapp JARs into the nodes' 'cordapps' directory. Possible values:
  FirstRunOnly, Yes, No. Default: FirstRunOnly.
* ``--verbose``, ``--log-to-console``, ``-v``: If set, prints logging to the console as well as to a file.
* ``--logging-level=<loggingLevel>``: Enable logging at this level and higher. Possible values: ERROR, WARN, INFO, DEBUG, TRACE. Default: INFO.
* ``--help``, ``-h``: Show this help message and exit.
* ``--version``, ``-V``: Print version information and exit.
* ``--minimum-platform-version``: The minimum platform version to use in the network-parameters.
* ``--max-message-size``: The maximum message size to use in the network-parameters, in bytes.
* ``--max-transaction-size``: The maximum transaction size to use in the network-parameters, in bytes.
* ``--event-horizon``: The event horizon to use in the network-parameters.
* ``--network-parameter-overrides=<networkParametersFile>``, ``-n=<networkParametersFile>``: Overrides the default network parameters with those
  in the given file. See `Overriding network parameters via a file`_ for more information.


Sub-commands
------------

``install-shell-extensions``: Install ``bootstrapper`` alias and auto completion for bash and zsh. See :doc:`cli-application-shell-extensions` for more info.
