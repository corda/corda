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

For these reasons, test deployments can avail themselves of the network bootstrapper. This is a tool that scans all the
node configurations from a common directory to generate the network parameters file, which is then copied to all the nodes'
directories. It also copies each node's node-info file to every other node so that they can all be visible to each other.

You can find out more about network maps and network parameters from :doc:`network-map`.

Bootstrapping a test network
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Corda Network Bootstrapper can be downloaded from `here <https://corda.net/resources>`_.

Create a directory containing a node config file, ending in "_node.conf", for each node you want to create. Then run the
following command:

``java -jar network-bootstrapper-VERSION.jar --dir <nodes-root-dir>``

For example running the command on a directory containing these files:

.. sourcecode:: none

    .
    ├── notary_node.conf             // The notary's node.conf file
    ├── partya_node.conf             // Party A's node.conf file
    └── partyb_node.conf             // Party B's node.conf file

will generate directories containing three nodes: ``notary``, ``partya`` and ``partyb``. They will each use the ``corda.jar``
that comes with the bootstrapper. If a different version of Corda is required then simply place that ``corda.jar`` file
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

Similarly, each node directory may contain its own ``corda.jar``, which the bootstrapper will use instead.

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

Whitelisting contracts
----------------------

Any CorDapps provided when bootstrapping a network will be scanned for contracts which will be used to create the
*Zone whitelist* (see :doc:`api-contract-constraints`) for the network.

.. note:: If you only wish to whitelist the CorDapps but not copy them to each node then run with the ``--no-copy`` flag.

The CorDapp JARs will be hashed and scanned for ``Contract`` classes. These contract class implementations will become part
of the whitelisted contracts in the network parameters (see ``NetworkParameters.whitelistedContractImplementations`` :doc:`network-map`).

By default the bootstrapper will whitelist all the contracts found in all the CorDapp JARs. To prevent certain
contracts from being whitelisted, add their fully qualified class name in the ``exclude_whitelist.txt``. These will instead
use the more restrictive ``HashAttachmentConstraint``.

For example:

.. sourcecode:: none

    net.corda.finance.contracts.asset.Cash
    net.corda.finance.contracts.asset.CommercialPaper

Modifying a bootstrapped network
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The network bootstrapper is provided as a development tool for setting up Corda networks for development and testing.
There is some limited functionality which can be used to make changes to a network, but for anything more complicated consider
using a :doc:`network-map` server.

When running the Network Bootstrapper, each ``node-info`` file needs to be gathered together in one directory. If
the nodes are being run on different machines you need to do the following:

* Copy the node directories from each machine into one directory, on one machine
* Depending on the modification being made (see below for more information), add any new files required to the root directory
* Run the Network Bootstrapper from the root directory
* Copy each individual node's directory back to the original machine

The network bootstrapper cannot dynamically update the network if an existing node has changed something in their node-info,
e.g. their P2P address. For this the new node-info file will need to be placed in the other nodes' ``additional-node-infos`` directory.
If the nodes are located on different machines, then a utility such as `rsync <https://en.wikipedia.org/wiki/Rsync>`_ can be used
so that the nodes can share node-infos.

Adding a new node to the network
--------------------------------

Running the bootstrapper again on the same network will allow a new node to be added and its
node-info distributed to the existing nodes.

As an example, if we have an existing bootstrapped network, with a Notary and PartyA and we want to add a PartyB, we
can use the network bootstrapper on the following network structure:

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

Then run the network bootstrapper again from the root dir:

``java -jar network-bootstrapper-VERSION.jar --dir <nodes-root-dir>``

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

The bootstrapper will generate a directory and the ``node-info`` file for PartyB, and will also make sure a copy of each
nodes' ``node-info`` file is in the ``additional-node-info`` directory of every node. Any other files in the existing nodes,
such a generated keys, will be unaffected.

.. note:: The bootstrapper is provided for test deployments and can only generate information for nodes collected on
    the same machine. If a network needs to be updated using the bootstrapper once deployed, the nodes will need
    collecting back together.

Updating the contract whitelist for bootstrapped networks
---------------------------------------------------------

If the network already has a set of network parameters defined (i.e. the node directories all contain the same network-parameters
file) then the bootstrapper can be used to append contracts from new CorDapps to the current whitelist.
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

Then run the network bootstrapper again from the root dir:

``java -jar network-bootstrapper-VERSION.jar --dir <nodes-root-dir>``

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

