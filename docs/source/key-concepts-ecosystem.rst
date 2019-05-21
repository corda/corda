The network
===========

.. topic:: Summary

   * *A Corda network is made up of nodes, each of which runs an instance of Corda and one or more CorDapps*
   * *Communication between nodes is point-to-point, and does not rely on global broadcasts*
   * *Each node has a certificate that maps its network identity to a real-world legal identity*
   * *The network is permissioned, with access requiring a certificate from the network operator*

Network structure
-----------------
A Corda network is a peer-to-peer network of **nodes**. Each node represents a legal entity, and each runs the Corda software (an instance of Corda and one or more Corda applications, known as **CorDapps**). 

.. image:: resources/network.png
   :scale: 25%
   :align: center

All communication between nodes is point-to-point and encrypted using transport-layer security. This means that data is
shared only on a need-to-know basis. There are **no global broadcasts**.

All of the nodes in the network have the *potential* to communicate with other nodes. 

**Why do we say "potential" to communicate?** 

Because the connections on the graph do not have to be persistent. On the networking level, Corda uses persistent queues, but, as with email, if your recipient is offline, your messages will wait on an outbound queue until the recipient comes online. 

Identities and Discovery
------------------------
Each node has a single well-known identity. The node's identity is used to represent the node in transactions; for example, if the node were involved in a transaction to purchase an asset.

The network map service maps each well-known node identity to an IP address. These IP
addresses are used for messaging between nodes.

Nodes can also generate confidential identities for individual transactions. The certificate chain linking a
confidential identity to a well-known node identity or real-world legal identity is only distributed on a need-to-know
basis. If confidential identities are being used, this ensures that even if an attacker gets access to an unencrypted transaction, they cannot identify the
transaction's participants without additional information.

**How do Corda nodes Discover each other?** 

Corda nodes discover each other via a **network map service**. You can think of this service as a phone book, which publishes a list of peer nodes that includes metadata about who they are and what services they can offer. 

Admission to the network
------------------------
Unlike traditional blockchain, Corda networks are semi-private. To join a network, a node must obtain a certificate from the network operator. This
certificate maps a well-known node identity to:

* A real-world legal identity
* A public key

The network operator enforces rules regarding the information that nodes must provide and the know-your-customer (KYC) processes they must undergo before being granted this certificate.

**What makes Corda different to other networks?** 

Other Distributed Ledger Technology (DLT) platforms use global broadcast and gossip networks to propagate data. Corda uses point-to-point messages, and sends them only on a need to know basis (lazy propagation).
