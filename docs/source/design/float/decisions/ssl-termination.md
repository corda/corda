![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: P2P Messaging Protocol
============================================

## Background / Context

Corda requires messages to be exchanged between nodes via a well-defined protocol. 

Determining this protocol is a critical upstream dependency for the design of key messaging components including the [float](../design.md).



## Options Analysis

### 1. Use AMQP

Under this option, P2P messaging will follow the [Advanced Message Queuing Protocol](https://www.amqp.org/).

#### Advantages

1.    As we have described in our marketing materials.
2.    Well-defined standard.
3.    Supportfor packet level flow control and explicit delivery acknowledgement.
4.    Will allow eventual swap out of Artemis for other brokers.

#### Disadvantages

1.    AMQP is a complex protocol with many layered state machines, for which it may prove hard to verify security properties. 
2.    No support for secure MAC in packets frames.
3.    No defined encryption mode beyond creating custom payloadencryption and custom headers.
4.    No standardised support for queue creation/enumeration, ordeletion.
5.    Use of broker durable queues and autonomousbridge transfers does not align with checkpoint timing, so that independentreplication of the DB and Artemis data risks causing problems. (Writing to the DB doesn’t work currently and is probably also slow).

### 2. Develop & implement a custom protocol

Under this option, P2P messaging will follow a custom protocol designed and implemented by the development team.

#### Advantages

1. Can be defined with very small message surface area that isamenable to security analysis.
2. Packet formats can follow best practice cryptography from thestart and be matched to Corda’s needs.
3. Doesn’t require ‘Complete Graph’ structure for network if we haveintermediate routing. 
4. More closely aligns checkpointing and message delivery handling atthe application level.

#### Disadvantages

1. Inconsistent with previous design statements published to external stakeholders.
2. Effort implications - starting from scratch
3. Technical complexity in developing a P2P protocols which is attack tolerant.



## Recommendation and justification

Proceed with Option 1



## Decision taken

Decision still required.