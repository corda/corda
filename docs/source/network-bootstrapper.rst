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

The bootstrapper can be downloaded from https://downloads.corda.net/network-bootstrapper-VERSION.jar, where ``VERSION``
is the Corda version.

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

The directory can also contain CorDapp JARs which will be copied to each node's ``cordapps`` directory.

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

Synchronisation
~~~~~~~~~~~~~~~

This tool only bootstraps a network. It cannot dynamically update if a new node needs to join the network or if an existing
one has changed something in their node-info, e.g. their P2P address. For this the new node-info file will need to be placed
in the other nodes' ``additional-node-infos`` directory. A simple way to do this is to use `rsync <https://en.wikipedia.org/wiki/Rsync>`_.
However, if it's known beforehand the set of nodes that will eventually form part of the network then all the node directories
can be pregenerated in the bootstrap and only started when needed.

Running the bootstrapper again on the same network will allow a new node to be added or an existing one to have its updated
node-info re-distributed. However this comes at the expense of having to temporarily collect the node directories back
together again under a common parent directory.

Whitelisting contracts
~~~~~~~~~~~~~~~~~~~~~~

The CorDapp JARs are also automatically used to create the *Zone whitelist* (see :doc:`api-contract-constraints`) for
the network.

.. note:: If you only wish to whitelist the CorDapps but not copy them to each node then run with the ``--no-copy`` flag.

The CorDapp JARs will be hashed and scanned for ``Contract`` classes. These contract class implementations will become part
of the whitelisted contracts in the network parameters (see ``NetworkParameters.whitelistedContractImplementations`` :doc:`network-map`).
If the network already has a set of network parameters defined (i.e. the node directories all contain the same network-parameters
file) then the new set of contracts will be appended to the current whitelist.

.. note:: The whitelist can only ever be appended to. Once added a contract implementation can never be removed.

By default the bootstrapper will whitelist all the contracts found in all the CorDapp JARs. To prevent certain
contracts from being whitelisted, add their fully qualified class name in the ``exclude_whitelist.txt``. These will instead
use the more restrictive ``HashAttachmentConstraint``.

For example:

.. sourcecode:: none

    net.corda.finance.contracts.asset.Cash
    net.corda.finance.contracts.asset.CommercialPaper
