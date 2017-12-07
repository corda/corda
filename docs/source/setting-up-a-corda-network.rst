.. _log4j2: http://logging.apache.org/log4j/2.x/

Creating a Corda network
========================

A Corda network consists of a number of machines running nodes. These nodes communicate using persistent protocols in
order to create and validate transactions.

There are three broader categories of functionality one such node may have. These pieces of functionality are provided
as services, and one node may run several of them.

* Notary: Nodes running a notary service witness state spends and have the final say in whether a transaction is a
  double-spend or not
* Oracle: Network services that link the ledger to the outside world by providing facts that affect the validity of
  transactions
* Regular node: All nodes have a vault and may start protocols communicating with other nodes, notaries and oracles and
  evolve their private ledger

Setting up your own network
---------------------------

Certificates
~~~~~~~~~~~~

Every node in a given Corda network must have an identity certificate signed by the network's root CA. See
:doc:`permissioning` for more information.

Configuration
~~~~~~~~~~~~~

A node can be configured by adding/editing ``node.conf`` in the node's directory. For details see :doc:`corda-configuration-file`.

An example configuration:

.. literalinclude:: example-code/src/main/resources/example-node.conf
    :language: cfg

The most important fields regarding network configuration are:

* ``p2pAddress``: This specifies a host and port to which Artemis will bind for messaging with other nodes. Note that the
  address bound will **NOT** be ``my-corda-node``, but rather ``::`` (all addresses on all network interfaces). The hostname specified
  is the hostname *that must be externally resolvable by other nodes in the network*. In the above configuration this is the
  resolvable name of a machine in a VPN.
* ``rpcAddress``: The address to which Artemis will bind for RPC calls.
* ``webAddress``: The address the webserver should bind. Note that the port must be distinct from that of ``p2pAddress`` and ``rpcAddress`` if they are on the same machine.

Bootstrapping the network
~~~~~~~~~~~~~~~~~~~~~~~~~

The nodes see each other using the network map. This is a collection of statically signed node-info files, one for each
node in the network. Most production deployments will use a highly available, secure distribution of the network map via HTTP.

For test deployments where the nodes (at least initially) reside on the same filesystem, these node-info files can be
placed directly in the node's ``additional-node-infos`` directory from where the node will pick them up and store them
in its local network map cache. The node generates its own node-info file on startup.

In addition to the network map, all the nodes on a network must use the same set of network parameters. These are a set
of constants which guarantee interoperability between nodes. The HTTP network map distributes the network parameters
which the node downloads automatically. In the absence of this the network parameters must be generated locally. This can
be done with the network bootstrapper. This a tool that scans all the node configurations from a common directory to
generate the network parameters file which is copied to the nodes' directories. It also copies each node's node-info file
to every other node.

The bootstrapper tool can be built with the command:

``gradlew buildBootstrapperJar``

The resulting jar can be found in ``tools/bootstrapper/build/libs/``.

To use it, run the following command, specifying the root directory which hosts all the node directories as the argument:

``java -jar network-bootstrapper.jar <nodes-root-dir>``

Starting the nodes
~~~~~~~~~~~~~~~~~~

You may now start the nodes in any order. You should see a banner, some log lines and eventually ``Node started up and registered``,
indicating that the node is fully started.

.. TODO: Add a better way of polling for startup. A programmatic way of determining whether a node is up is to check whether it's ``webAddress`` is bound.

In terms of process management there is no prescribed method. You may start the jars by hand or perhaps use systemd and friends.

Logging
~~~~~~~

Only a handful of important lines are printed to the console. For
details/diagnosing problems check the logs.

Logging is standard log4j2_ and may be configured accordingly. Logs
are by default redirected to files in ``NODE_DIRECTORY/logs/``.

Connecting to the nodes
~~~~~~~~~~~~~~~~~~~~~~~

Once a node has started up successfully you may connect to it as a client to initiate protocols/query state etc.
Depending on your network setup you may need to tunnel to do this remotely.

See the :doc:`tutorial-clientrpc-api` on how to establish an RPC link.

Sidenote: A client is always associated with a single node with a single identity, which only sees their part of the ledger.
