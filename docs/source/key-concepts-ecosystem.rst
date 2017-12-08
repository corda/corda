The network
===========

.. topic:: Summary

   * *A Corda network is made up of nodes running Corda and CorDapps*
   * *The network is permissioned, with access controlled by a doorman*
   * *Communication between nodes is point-to-point, instead of relying on global broadcasts*

Network structure
-----------------
A Corda network is an authenticated peer-to-peer network of nodes, where each node is a JVM run-time environment
hosting Corda services and executing applications known as *CorDapps*.

All communication between nodes is direct, with TLS-encrypted messages sent over AMQP/1.0. This means that data is
shared only on a need-to-know basis; in Corda, there are **no global broadcasts**.

Each network has a **network map service** that publishes the IP addresses through which every node on the network can
be reached, along with the identity certificates of those nodes and the services they provide.

The doorman
-----------
Corda networks are semi-private. Each network has a doorman service that enforces rules regarding the information
that nodes must provide and the know-your-customer processes that they must complete before being admitted to the
network.

To join the network, a node must contact the doorman and provide the required information. If the doorman is
satisfied, the node will receive a root-authority-signed TLS certificate from the network's permissioning service.
This certificate certifies the node's identity when communicating with other participants on the network.

We can visualize a network as follows:

.. image:: resources/network.png
   :scale: 25%
   :align: center

Network services
----------------
Nodes can provide several types of services:

* One or more pluggable **notary services**. Notaries guarantee the uniqueness, and possibility the validity, of ledger
  updates. Each notary service may be run on a single node, or across a cluster of nodes.
* Zero or more **oracle services**. An oracle is a well-known service that signs transactions if they state a fact and
  that fact is considered to be true.