A Corda network


Introduction - What is a corda network?
========================================================

A Corda network consists of a number of machines running ``node``s, including a single node operating as the network map service. These nodes communicate using persistent protocols in order to create and validate transactions.

There are four broader categories of functionality one such node may have. These pieces of functionality are provided as services, and one node may run several of them.

* Network map: The node running the network map provides a way to resolve identities to physical node addresses.
* Notary: Nodes running a notary service witness state spends and have the final say in whether a transaction is a double-spend or not.
* Oracle: Nodes providing some oracle functionality like exchange rate or interest rate witnesses.
* Regular node: All nodes have a vault and may start protocols communicating with other nodes, notaries and oracles and evolve their private ledger.

Setting up your own network
===========================

Certificates
------------

All node certificates' root must be the same. Later R3 will provide the root for production use, but for testing you can use ``certSigningRequestUtility.jar`` to generate a node certificate with a fixed test root:

.. sourcecode:: bash

    # Build the jars
    ./gradlew buildCordaJAR
    # Generate certificate
    java -jar build/libs/certSigningRequestUtility.jar --base-dir NODE_DIRECTORY/

Configuration
-------------

A node can be configured by adding/editing ``node.conf`` in the node's directory.

An example configuration:

.. literalinclude:: example-code/src/main/resources/example-node.conf
    :language: cfg

The most important fields regarding network configuration are:

* ``artemisAddress``: This specifies a host and port. Note that the address bound will **NOT** be ``cordaload-node1``, but rather ``::`` (all addresses on all interfaces). The hostname specified is the hostname *that must be externally resolvable by other nodes in the network*. In the above configuration this is the resolvable name of a node in a vpn.
* ``webAddress``: The address the webserver should bind. Note that the port should be distinct from that of ``artemisAddress``.
* ``networkMapAddress``: The resolvable name and artemis port of the network map node. Note that if this node itself is to be the network map this field should not be specified.

Starting the nodes
------------------

You may now start the nodes in any order. You should see lots of log lines about the startup.

Note that the node is not fully started until it has successfully registered with the network map! A good way of determining whether a node is up is to check whether its ``webAddress`` is bound.

In terms of process management there is no pre-described method, you may start the jars by hand or perhaps use systemd and friends.

Connecting to the nodes
-----------------------

Once a node has started up successfully you may connect to it as a client to initiate protocols/query state etc. Depending on your network setup you may need to tunnel to do this remotely.

See the :doc:`tutorial-clientrpc-api` on how to establish an RPC link, or you can use the web apis as well.

Sidenote: A client is always associated with a single node with a single identity, which only sees their part of the ledger.
