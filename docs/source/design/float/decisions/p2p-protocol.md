# Design Decision: P2P Messaging Protocol

## Background / Context

Corda requires messages to be exchanged between nodes via a well-defined protocol. 

Determining this protocol is a critical upstream dependency for the design of key messaging components including the [float](../design.md).

## Options Analysis

### 1. Use AMQP

Under this option, P2P messaging will follow the [Advanced Message Queuing Protocol](https://www.amqp.org/).

#### Advantages

1. As we have described in our marketing materials.
2. Well-defined standard.
3. Support for packet level flow control and explicit delivery acknowledgement.
4. Will allow eventual swap out of Artemis for other brokers.

#### Disadvantages

1. AMQP is a complex protocol with many layered state machines, for which it may prove hard to verify security properties. 
2. No support for secure MAC in packets frames.
3. No defined encryption mode beyond creating custom payload encryption and custom headers.
4. No standardised support for queue creation/enumeration, or deletion.
5. Use of broker durable queues and autonomous bridge transfers does not align with checkpoint timing, so that independent replication of the DB and Artemis data risks causing problems. (Writing to the DB doesn’t work currently and is probably also slow).

### 2. Develop a custom protocol

This option would discard existing Artemis server/AMQP support for peer-to-peer communications in favour of a custom
implementation of the Corda MessagingService, which takes direct responsibility for message retries and stores the
pending messages into the node's database. The wire level of this service would be built on top of a fully encrypted MIX
network which would not require a fully connected graph, but rather send messages on randomly selected paths over the
dynamically managed network graph topology.

Packet format would likely use the [SPHINX packet format](http://www0.cs.ucl.ac.uk/staff/G.Danezis/papers/sphinx-eprint.pdf) although with the body encryption updated to
a modern AEAD scheme as in https://www.cs.ru.nl/~bmennink/pubs/16cans.pdf . In this scheme, nodes would be identified in
the overlay network solely by Curve25519 public key addresses and floats would be dumb nodes that only run the MIX
network code and don't act as message sources, or sinks. Intermediate traffic would not be readable except by the
intended waypoint and only the final node can read the payload.

Point to point links would be standard TLS and the network certificates would be whatever is acceptable to the host
institutions e.g. standard Verisign certs. It is assumed institutions would select partners to connect to that they
trust and permission them individually in their firewalls. Inside the MIX network the nodes would be connected mostly in
a static way and use standard HELLO packets to determine the liveness of neighbour routes, then use tunnelled gossip to
distribute the signed/versioned Link topology messages. Nodes will also be allowed to advertise a public IP, so some
dynamic links and publicly visible nodes would exist. Network map addresses would then be mappings from Legal Identity
to these overlay network addresses, not to physical network locations.

#### Advantages

1. Can be defined with very small message surface area that is amenable to security analysis.
2. Packet formats can follow best practice cryptography from the start and be matched to Corda’s needs.
3. Doesn’t require a complete graph structure for network if we have intermediate routing. 
4. More closely aligns checkpointing and message delivery handling at the application level.

#### Disadvantages

1. Inconsistent with previous design statements published to external stakeholders.
2. Effort implications - starting from scratch
3. Technical complexity in developing a P2P protocols which is attack tolerant.

## Recommendation and justification

Proceed with Option 1

## Decision taken

Proceed with Option 1 - Continue to use AMQP (RGB, JC, MH agreed)

.. toctree::

   drb-meeting-20171116.md
