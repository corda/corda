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

Messaging types
---------------

Every ``Message`` object has an associated *topic* and may have a *session ID*. These are wrapped in a ``TopicSession``.
An implementation of ``MessagingService`` can be used to create messages and send them. You can get access to the
messaging service via the ``ServiceHub`` object that is provided to your app. Endpoints on the network are
identified at the lowest level using ``SingleMessageRecipient`` which may be e.g. an IP address, or in future
versions perhaps a routing path through the network.

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
  The code simply picks the first registered node that supports the required service.