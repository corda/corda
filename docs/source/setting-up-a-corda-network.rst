.. _log4j2: http://logging.apache.org/log4j/2.x/

Setting up a Corda network
==========================

.. contents::

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
* ``notary.serviceLegalName``: The name of the notary service, required to setup distributed notaries with the network-bootstrapper.

Starting the nodes
~~~~~~~~~~~~~~~~~~

You will first need to create the local network by bootstrapping it with the bootstrapper. Details of how to do that can
be found in :doc:`network-bootstrapper`.

Once that's done you may now start the nodes in any order. You should see a banner, some log lines and eventually
``Node started up and registered``, indicating that the node is fully started.

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
