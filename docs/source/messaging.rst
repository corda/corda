Networking and messaging
========================

Corda uses AMQP/1.0 over TLS between nodes which is currently implemented using Apache Artemis, an embeddable message
queue broker. Building on established MQ protocols gives us features like persistence to disk, automatic delivery
retries with backoff and dead-letter routing, security, large message streaming and so on.

Artemis is hidden behind a thin interface that also has an in-memory only implementation suitable for use in
unit tests and visualisation tools.

.. note:: A future version of Corda will allow the MQ broker to be split out of the main node and run as a
   separate server. We may also support non-Artemis implementations via JMS, allowing the broker to be swapped
   out for alternative implementations.

There are multiple ways of interacting with the network. When writing an application you typically won't use the
messaging subsystem directly. Instead you will build on top of the :doc:`flow framework <flow-state-machines>`,
which adds a layer on top of raw messaging to manage multi-step flows and let you think in terms of identities
rather than specific network endpoints.

.. _network-map-service:

Network Map Service
-------------------

Supporting the messaging layer is a network map service, which is responsible for tracking public nodes on the network.

Nodes have an internal component, the network map cache, which contains a copy of the network map (which is just a
document). When a node starts up its cache fetches a copy of the full network map, and requests to be notified of
changes. The node then registers itself with the network map service, and the service notifies subscribers that a new
node has joined the network. Nodes do not automatically deregister themselves, so (for example) nodes going offline
briefly for maintenance are retained in the network map, and messages for them will be queued, minimising disruption.

Nodes submit signed changes to the map service, which then forwards update notifications on to nodes which have
requested to be notified of changes.

The network map currently supports:

* Looking up nodes by service
* Looking up node for a party
* Suggesting a node providing a specific service, based on suitability for a contract and parties, for example suggesting
  an appropriate interest rates oracle for a interest rate swap contract. Currently no recommendation logic is in place.

Message queues
--------------

The node makes use of various queues for its operation. The more important ones are described below. Others are used
for maintenance and other minor purposes.

:``p2p.inbound``:
   The node listens for messages sent from other peer nodes on this queue. Only clients who are authenticated to be
   nodes on the same network are given permission to send. Messages which are routed internally are also sent to this
   queue (e.g. two flows on the same node communicating with each other).

:``internal.peers.$identity``:
   These are a set of private queues only available to the node which it uses to route messages destined to other peers.
   The queue name ends in the base 58 encoding of the peer's identity key. There is at most one queue per peer. The broker
   creates a bridge from this queue to the peer's ``p2p.inbound`` queue, using the network map service to lookup the
   peer's network address.

:``internal.services.$identity``:
   These are private queues the node may use to route messages to services. The queue name ends in the base 58 encoding
   of the service's owning identity key. There is at most one queue per service identity (but note that any one service
   may have several identities). The broker creates bridges to all nodes in the network advertising the service in
   question. When a session is initiated with a service counterparty the handshake is pushed onto this queue, and a
   corresponding bridge is used to forward the message to an advertising peer's p2p queue. Once a peer is picked the
   session continues on as normal.

:``internal.networkmap``:
   This is another private queue just for the node which functions in a similar manner to the ``internal.peers.*`` queues
   except this is used to form a connection to the network map node. The node running the network map service is treated
   differently as it provides information about the rest of the network.

:``rpc.requests``:
   RPC clients send their requests here, and it's only open for sending by clients authenticated as RPC users.

:``clients.$user.rpc.$random``:
   RPC clients are given permission to create a temporary queue incorporating their username (``$user``) and sole
   permission to receive messages from it. RPC requests are required to include a random number (``$random``) from
   which the node is able to construct the queue the user is listening on and send the response to that. This mechanism
   prevents other users from being able listen in on the responses.

Security
--------

Clients attempting to connect to the node's broker fall in one of four groups:

#. Anyone connecting with the username ``SystemUsers/Node`` is treated as the node hosting the broker, or a logical
   component of the node. The TLS certificate they provide must match the one broker has for the node. If that's the case
   they are given full access to all valid queues, otherwise they are rejected.

#. Anyone connecting with the username ``SystemUsers/Peer`` is treated as a peer on the same Corda network as the node. Their
   TLS root CA must be the same as the node's root CA - the root CA is the doorman of the network and having the same root CA
   implies we've been let in by the same doorman. If they are part of the same network then they are only given permission
   to send to our ``p2p.inbound`` queue, otherwise they are rejected.

#. Every other username is treated as a RPC user and authenticated against the node's list of valid RPC users. If that
   is successful then they are only given sufficient permission to perform RPC, otherwise they are rejected.

#. Clients connecting without a username and password are rejected.

Artemis provides a feature of annotating each received message with the validated user. This allows the node's messaging
service to provide authenticated messages to the rest of the system. For the first two client types described above the
validated user is the X.500 subject DN of the client TLS certificate and we assume the common name is the legal name of
the peer. This allows the flow framework to authentically determine the ``Party`` initiating a new flow. For RPC clients
the validated user is the username itself and the RPC framework uses this to determine what permissions the user has.

.. note:: ``Party`` lookup is currently done by the legal name which isn't guaranteed to be unique. A future version will
   use the full X.500 name as it can provide additional structures for uniqueness.

The broker also does host verification when connecting to another peer. It checks that the TLS certificate common name
matches with the advertised legal name from the network map service.

Messaging types
---------------

Every ``Message`` object has an associated *topic* and may have a *session ID*. These are wrapped in a ``TopicSession``.
An implementation of ``MessagingService`` can be used to create messages and send them. You can get access to the
messaging service via the ``ServiceHub`` object that is provided to your app. Endpoints on the network are
identified at the lowest level using ``SingleMessageRecipient`` which may be e.g. an IP address, or in future
versions perhaps a routing path through the network.
