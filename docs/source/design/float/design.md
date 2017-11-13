![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Float Design

============================================
DOCUMENT MANAGEMENT
============================================

## Document Control

* Title: Float Design
* Date: 13th November 2018
* Author: Matthew Nesbit
* Distribution: Design Review Board, Product Management, Services - Technical (Consulting), Platform Delivery
* Corda target version: Enterprise

## Document Sign-off

* Author: David Lee
* Reviewers(s): TBD
* Final approver(s): TBD

## Document History

============================================
HIGH LEVEL DESIGN
============================================

## Overview

The role of the 'float' is to meet the requirements of organisations that will not allow direct incoming connections to their node, but would rather host a proxy component in a DMZ to achieve this. As such it needs to meet the requirements of modern DMZ security rules, which essentially assume that the entire machine in the DMZ may become compromised.  At the same time, we expect that the Float can interoperate with directly connected nodes, possibly even those using open source Corda.

### Background

Typical modern DMZ rules are:
1. There shall be a firewall between the internet and the DMZ machine and a further firewall between the DMZ and the internal network. Only identified IP's and ports are permitted to access the DMZ box. This include intra-DMZ communications.
2. The DMZ box is typically multi-homed with a network card facing towards the institutional firewall and one facing the internet. (There is usually a further hidden management interface card accessed via a jump box for managing the box and shipping audit trail information). This requires that our software can bind listening ports to the correct network card not just to 0.0.0.0.
3. It is best practice to allow no connections to be initiated by the DMZ box towards the internal network. Communications should be initiated by the internal network to form a bidirectional channel with the proxy process.
4. It is usually required that no business data is persisted on the DMZ box.
5. An audit log of all connection events is almost always required to track breaches. Ideally some latency information is also tracked to deal with connectivity issues.
6. The processes on the DMZ box typically run as local accounts with no relationship to the internal permission systems, or ability to enumerate the internal network.
7. Communications in the DMZ should be modern TLS, often with local only certificates/keys that are of no value outside of the predefined links.
8. It is common to terminate the TLS on the firewall which has an associated HSM for the private keys. This means that we do not necessarily have the certificates of the connection, but hopefully for now we can insist on receiving the connection directly onto the float proxy, although we have to ask how we might access an HSM.
9. It is usually assumed that there is an HA/load balancing pair (or more) of proxies for resilience. Often the firewalls are also combined with hardware load balancer functionality.
10. Ideally any business data passing through the proxy should be separately encrypted, so that no data is in the clear of the program memory if the DMZ box is compromised. I doubt we can finish end-to-end session encryption by March, but we should define our AMQP packet structure to be forward compatible with a switching flag so that we can leave encryption till later.

## Scope

* Goals
* Non-goals (eg. out of scope)
* Reference(s) to similar or related work

## Timeline 

## Requirements

## Proposed Solution

### Bridge Control Protocol
My proposal is to make the bridge control as stateless as possible. Thus, nodes and bridges restarting must re-request/broadcast information to each other. The messages should be sent to a 'bridge.control' address in Artemis and be sent as non-persistent messages with a non-durable queue, each message should contain a duplicate message ID, which is also re-used as the correlation id in replies. The scenarios are:

#### On bridge start-up, or reconnection to Artemis
1. The bridge process should subscribe to the 'bridge.control'.
2. The bridge should start sending QueueQuery messages which will contain a unique message id and an identifier for the bridge sending the message.
3. The bridge should continue to send these until at least one node replies with a matched QueueSnapshot message.
4. The QueueSnapshot message replies from the nodes contains a correlationId field set to the unique id of the QueueQuery query, or the correlation id is null. The message payload is a list of inbox queue info items and a list of outbound queue info items. Each queue info item is a tuple of Legal X500 Name (as expected upon the destination TLS certificates) and the queue name which should have the form of "internal.peers."+hash key of legal identity (using the same algorithm as  we use in the db to make the string). Note this queue name is a change from the current logic, but will be more portable to length constrained topics and allow multiple inboxes on the same broker.
5. The bridge should process the QueueSnapshot, initiating links to the outgoing targets. It should also add expected inboxes to its in-bound permission list.
6. When an outgoing link is successfully formed the remote client certificate should be checked against the expected X500 name. Assuming the link is valid the bridge should subscribe to the related queue and start trying to forward the messages.

#### On node start-up, or reconnection to Artemis
1. The node should subscribe to 'bridge.control'.
2. The node should enumerate the queues and identify which are have well known identities in the network map cache. The appropriate information about its own inboxes and any known outgoing queues should be compiled into an unsolicited QueueSnapshot message with a null correlation id. This should be broadcasted to update any bridges that are running.
3. If any QueueQuery messages arrive these should be responded to with specific QueueSnapshot messages with the correlation id set.

#### On network map updates
1. On receipt of any network map cache updates the information should be evaluated to see if any addition queues can now be mapped to a bridge. At this point a BridgeRequest packet should be sent which will contain the legal X500Name and queue name of the new update.

#### On flow message to Peer
1. If a message is to be sent to a peer the code should (as it does now) check for queue existence in its cache and then on the broker. If it does exist it simply sends the message.
2. If the queue is not listed in its cache it should block until the queue is created (this should be safe versus race conditions with other nodes).
3. Once the queue is created the original message and subsequent messages can now be sent.
4. In parallel a BridgeRequest packet should be sent to activate a new connection outwards. This will contain the contain the legal X500Name and queue name of the new queue.
5. Future QueueSnapshot requests should be responded to with the new queue included in the list.

#### Behaviour with a Float portion in the DMZ
1. With the Float in the DMZ there are potentially two options, either the float can initiate outgoing bridges, or we make it a listener only. After some discussion, it seems that there have been requests to separate in inbound and outbound paths, so for now I model the float as a listener only. The internal portion of the bridge being allowed to initiate through the firewall (possibly via a SOCKS proxy).
2. On initial connection of the inbound bridge connection the Float should authenticate to the best of its ability the origin of the link. If this is a direct termination of the TLS connection then the client certificate must go back to the Corda trust root. Also, the X500 name of the certificate should be recorded and appended to any forwarded messages to the internal systems.
3. If the connection to the Float is not direct, then the AMQP should be configured to run a SASL challenge response to revalidate the origin. The most likely SASL mechanism for this is using https://tools.ietf.org/html/rfc3163 as this allows reuse of our PKI certificates in the challenge response. This should allow us to confirm the client identity. Potentially we could forward some bridge control messages to cover the SASL exchange to the internal Bridge Controller. This would allow us to keep the private keys internal to the organisation, so we may also require a SASLAuth message type as part of the bridge control protocol.
4. The float should restrict acceptable AMQP topics to the name space appropriate for inbound messages only i.e. there should be no way to tunnel messages to bridge control, or RPC topics on the bus.
5. On receipt of a message from the external network the Float should append a header to link the source channel's X500 name, then create a Delivery for forwarding the message inwards.
6. The internal Bridge Control Manager process should validate the message further to ensure that it is targeted at a legitimate inbox (i.e. not an outbound queue) and then forward to the bus. Once delivered to the broker the Delivery acknowledgements should be cascaded back.
7. The Float on receiving Delivery notification from the internal side should acknowledge back the correlated original Delivery.
8. The Float should protect against excessive inbound messages by AMQP flow control and refusing to accept excessive unacknowledged deliveries.
9. The Float should only expose its inbound server socket when activated by a valid AMQP link from the Bridge Control Manager to allow for a simple HA pool of DMZ Float processes. We cannot run the Floats hot-hot as this would invalidate our message ordering guarantees.

### Challenges and Unanswered Questions

The main uncertainty for the Float design is key management for the private key portion of the TLS certificate. This is likely to reside inside an HSM and it is unlikely to be accessible from the DMZ servers. It may be possible to tunnel the PrivateKey signing step to the internal Bridge Control Manager, but this makes things complicated. However, it is common for this to be configured inside the firewall, although we will have to see our non-standard PKI interacts with a typical firewall.zt

The other uncertainty is if/how we should provide end-to-end encryption of the business data. I think it is inevitable that this will be desired, so we should allow for it in our wire format. However, to properly implement this with session keys and properly authenticated encryption is a significant design task. (At minimum, we would probably use some form of Ephemeral-Static Diffie Hellman against the remote Legal Identity to create the session secret and then AES-GCM, or similar AEAD for the message data. The AMQP headers would also need to be protected in this process, along with careful choice of IV to prevent any collisions.)


## Alternative Options

List any alternative solutions that may be viable but not recommended.

## Final recommendation

Proposed solution (if more than one option presented)
Proceed direct to implementation
Proceed to Technical Design stage
Proposed Platform Technical team(s) to implement design (if not already decided)

============================================
IMPLEMENTATION PLAN
============================================

# Proposed Incremental Steps Towards a Float
1. First, I would like to more explicitly split the RPC and P2P MessagingService instances inside the Node. They can keep the same interface, but this would let us develop P2P and RPC at different rates if required.
2. The current in-node design with Artemis Core bridges should first be replaced with an equivalent piece of code that initiates send only bridges using an in-house wrapper over the proton-j library. Thus, the current Artemis message objects will be picked up from existing queues using the CORE protocol via an abstraction interface to allow later pluggable replacement. The specific subscribed queues are controlled as before and bridges started by the existing code path. The only difference is the bridges will be the new AMQP client code. The remote Artemis broker should accept transferred packets directly onto its own inbox queue and acknowledge receipt via standard AMQP Delivery notifications. This in turn will be acknowledged back to the Artemis Subscriber to permanently remove the message from the source Artemis queue. The headers for deduplication, address names, etc will need to be mapped to the AMQP messages and we will have to take care about the message payload. This should be an envelope that is capable in the future of being end-to-end encrypted. Where possible we should stay close to the current Artemis mappings.
3. We need to define a bridge control protocol, so that we can have an out of process float/bridge.  The current process is that on message send the node checks the target address to see if the target queue already exists. If the queue doesn't exist it creates a new queue which includes an encoding of the PublicKey in its name. This is picked up by a wrapper around the Artemis Server which is also hosted inside the node and can ask the network map cache for a translation to a target host and port. This in turn allows a new bridge to be provisioned. At node restart the re-population of the network map cache is followed to re-create the bridges to any unsent queues/messages.
4. My proposal for a bridge control protocol is partly influenced by the fact that AMQP does not have a built-in mechanism for queue creation/deletion/enumeration. Also, the flows cannot progress until they are sure that there is an accepting queue. Finally, if one runs a local broker it should be fine to run multiple nodes without any bridge processes. Therefore, I will leave the queue creation as the node's responsibility. Initially we can continue to use the existing CORE protocol for this. The requirement to initiate a bridge will change from being implicit signalling via server queue detection to being an explicit pub-sub message that requests bridge formation. This doesn't need durability, or acknowledgements, because when a bridge process starts it should request a refresh of the required bridge list. The typical create bridge messages should contain: 1. The queue name (ideally with the sha256 of the PublicKey, not the whole PublicKey as that may not work on brokers with queue name length constraints). 2.  The expected X500Name for the remote TLS certificate. 3. The list of host and ports to attempt connection to. See separate section for more info.
5. Once we have the bridge protocol in place and a bridge out of process the broker can move out of process too, which is a requirement for clustering anyway. We can then start work on floating the bridge and making our broker pluggable.
a. At this point the bridge connection to the local queues should be upgraded to also be AMQP client, rather than CORE protocol, which will give the ability for the P2P bridges to work with other broker products.
b. An independent task is to look at making the Bridge process HA, probably using a similar hot-warm mastering solution as the node, or atomix.io. The inactive node should track the control messages, but obviously doesn't initiate any bridges.
c. Another potentially parallel piece of development is to start to build a float, which is essentially just splitting the bridge in two and putting in an intermediate hop AMQP/TLS link. The thin proxy in the DMZ zone should be as stateless as possible in this.
d. Finally, the node should use AMQP to talk to its local broker cluster, but this will have to remain partly tied to Artemis, as queue creation will require sending management messages to the Artemis core, but we should be able to abstract this. Bridge Management Protocol.
