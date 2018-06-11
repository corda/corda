Corda Bridge Component Overview
===============================

.. contents::

Introduction
------------
The Corda bridge/float component is designed for enterprise deployments and acts as an application level
firewall and protocol break on all internet facing endpoints. The ``corda-bridgeserver.jar`` encapsulates the peer
network functionality of the basic Corda node, so that this can be operated separately from the security sensitive
JVM runtime of the node. This gives separation of functionality and ensures that the legal identity keys are not
used in the same process as the internet TLS connections. Also, it adds support for enterprise deployment requirements,
such as High Availability (HA) and SOCKS proxy support.

This document is intended to provide an overview of the architecture and options available.

Terminology
-----------
The component referred to here as the *bridge* is the library of code responsible for managing outgoing links to peer
nodes and implements the AMQP 1.0 protocol over TLS 1.0 between peers to provide reliable flow message delivery. This
component can be run as a simple integrated feature of the node. However, for enhanced security and features in Corda
Enterprise, the in-node version should be turned off and a standalone and HA version can be run from the
``corda-bridgeserver.jar``, possibly integrating with a SOCKS proxy.

The *float* component refers to the inbound socket listener, packet filtering and DMZ compatible component. In the
simple all-in-one node all inbound peer connections terminate directly onto an embedded Artemis broker component
hosted within the node. The connection authentication and packet the filtering is managed directly via Artemis
permission controls managed directly inside the node JVM. For Corda Enterprise deployments we provide a more
secure and configurable isolation component that is available using code inside ``corda-bridgeserver.jar``. This
component is designed to provide a clear protocol break and thus prevents the node and Artemis server ever being
directly exposed to peers. For simpler deployments with no DMZ the float and bridge logic can also be run as a
single application behind the firewall, but still protecting the node and hosted Artemis. In future we may also host
the Artemis server out of process and shared across nodes, but this will be transparent to peers as the interchange
protocol will continue to be AMQP 1.0 over TLS.

.. Note:: All deployment modes of the bridge, float, or all-in-one node are transparently interoperable, if correctly configured.

Message Path Between Peer Nodes
-------------------------------
When a flow within a node needs to send a message to a peer there is a carefully orchestrated sequence of steps to ensure
correct secure routing based upon the network map information and to ensure safe, restartable delivery to the remote flow.
Adding the bridge and float to this process adds some extra steps and security checking of the messages.
The complete sequence is therefore:

1.   The flow calls ``send``, or ``sendAndReceive`` to propagate a message to a peer. This leads to checkpointing
     of the flow fiber within the ``StateMachine`` and posting the message to the internal ``MessagingService``. This ensures that
     the send activity will be retried if there are any errors before further durable transmission of the message.

2.   The ``MessagingService`` checks if this is a new destination node and if an existing out queue and bridge exists in Artemis.
     If the durable out queue does not exist then this will need to be created in Artemis:

     a.   First the durable queue needs to be created in the peer-to-peer Artemis. Each queue is uniquely named based upon the hash of the
          legal identity ``PublicKey`` of the target node.

     b.   Once the queue creation is complete a bridge creation request is also published onto the Artemis bus via the bridge control protocol.
          This message uses information from the network map to link the out queue to the target host and port and TLS credentials.
          The flow does not need to wait for any response at this point and can carry on to send messages to the Artemis out queue.

     c.   The message when received by the bridge process opens a TLS connection to the remote peer (optionally, this
          connection can be made via a SOCKS4/5 proxy). On connect the two ends of the TLS link exchange certificate details
          and confirm that the certificate path is anchored at the network root certificate and that the X500 subject matches
          the expected target as specified in the create bridge message using details contained in the network map.
          The links are long lived so as to reduce the setup cost of the P2P messaging.
          In future, there may also be denial-of-service protection measures applied.

     d.   If the outgoing TLS 1.2 link is created successfully then the bridge opens a consumer on the Artemis out queue.
          The pending messages will then be transferred to the remote destination using AMQP 1.0, with final removal from the
          out queue only occurring when the remote end fully acknowledges safe message receipt. This ensures at least once
          delivery semantics.

     e.   Note that at startup of either the node or the bridge, the bridge control protocol resynchronises the bridging state,
          so that all out queues have an active bridge.

3.   Assuming an out queue exists the message can be posted to Artemis and the bridge should eventually deliver this
     message to the remote system.

4.   On receipt of a message acknowledge from Artemis the ``StateMachine`` can continue flow if it is not awaiting a response
     i.e. a ``send`` operation. Otherwise it remains suspended waiting for the reply.

5.   The receiving end of the bridge TLS/AMQP 1.0 link might be the Artemis broker of a remote node,
     but for now we assume it is an enterprise deployment that is using a float process running behind a firewall.
     The receiver will already have confirmed the validity of the TLS originator when it accepted the TLS handshake.
     However, the float does some further basic checking of received messages and their associated headers.
     For instance the message must be targeted at an inbox address and must be below the network parameters defined ``maxMessageSize``.

6.   Having passed initial checks on the message the float bundles up the message and originator as a payload to be
     sent across the DMZ internal firewall. This inbound message path uses a separate AMQP 1.0/TLS control tunnel.
     (N.B. This link is initiated from the local master bridge in the trusted zone to the float in the DMZ. This allows a
     simple firewall rule to be configured which blocks any attempts to probe the internal network from the DMZ.)
     Once the message is forwarded the float keeps track of the delivery acknowledgements,
     so that the original sender will consume the message in the source queue, only on final delivery to the peer inbox.
     Any disconnections, or problems will send a reject status leading to redelivery from source.

7.   The bridge process having now received custody of the message does further checks that the message is good. At the
     minute the checks are essentially of well formedness of the message and that the source and destination are valid.
     However, future enhancements may include deep inspection of the message payload for CorDapp blacklisting, and other purposes.
     Any problems and the message is acknowledged to prevent further redelivery, logged to audit and dropped.

8.   Assuming this is a normal message it is passed onto the Artemis inbox and on acknowledgment of delivery
     is cascaded back. Thus, Artemis acknowledgement, leads to acknowledgement of the tunnel AMQP packet,
     which acknowledges the AMQP back to the sending bridge and that finally marks the Artemis out queue item as consumed.
     To prevent this leading to very slow one after the other message delivery the AMQP channels using sliding window flow control.
     (Currently, a practical default is set internally and the window size is not user configurable.)

9.   The ``MessagingService`` on the peer node will pick up the message from inbox on Artemis, carry out any necessary
     deduplication. This deduplication is needed as the distributed restartable logic of the Corda wire protocol only
     offers 'at least once' delivery guarantees.
     The resulting unique messages are then passed to the ``StateMachine`` so that the remote flow can be woken up.

10.  The reply messages use the authenticated originator flag attached by the float to route the replies back to the
     correct originator.

     .. Note::   That the message reply path is not via the inbound path, but instead is via a separately validated route
        from the local bridge to the original node's float and then on to the original node via Artemis.

Operating modes of the Bridge and Float
---------------------------------------

Embedded Developer Node (node + artemis + internal bridge, no float, no DMZ)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The simplest development deployment of the bridge is to just use the embedded Peer-to-Peer Artemis with the node as TLS endpoint
and to have the outgoing packets use the internal bridge functionality. Typically this should only be used for easy development,
or for organisations evaluating on Open Source Corda, where this is the only available option:

.. image:: resources/bridge/node_embedded_bridge.png
     :scale: 100%
     :align: center

Node + Bridge (no float, no DMZ)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The next simplest deployment is to turn off the built in bridge using the ``externalBridge`` enterprise config property
and to run a single combined bridge/float process. This might be suitable for a test environment, to conserve VM's.

 .. note::  Note that to run the bridge and the node on the same machine there could be a port conflict with a naive setup,
            but by using the ``messagingServerAddress`` property to specify the bind address and port plus setting
            ``messagingServerExternal = false``
            the embedded Artemis P2P broker can be set to listen on a different port rather than the advertised ``p2paddress`` port.
            Then configure an all-in-one bridge to point at this node:

.. image:: resources/bridge/simple_bridge.png
     :scale: 100%
     :align: center

DMZ ready (node + bridge + float)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To familiarize oneself with the a more complete deployment including a DMZ and separated inbound and outbound paths
the ``bridgeMode`` property in the ``bridge.conf`` should be set to ``BridgeInner`` for the bridge and
``FloatOuter`` for the DMZ float. The diagram below shows such a non-HA deployment. This would not be recommended
for production, unless used as part of a cold DR type standby.

.. note::  Note that whilst the bridge needs access to the official TLS private
        key, the tunnel link should use a private set of link specific keys and certificates. The float will be provisioned
        dynamically with the official TLS key when activated via the tunnel and this key will never be stored in the DMZ:

.. image:: resources/bridge/bridge_and_float.png
     :scale: 100%
     :align: center

DMZ ready with outbound SOCKS
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Some organisations require dynamic outgoing connections to operate via a SOCKS proxy. The code supports this option
by adding extra information to the ``outboundConfig`` section of the bridge process. An simplified example deployment is shown here
to highlight the option:

.. image:: resources/bridge/bridge_with_socks.png
     :scale: 100%
     :align: center

Full production HA DMZ ready mode (hot/cold node, hot/warm bridge)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Finally, we show a full HA solution as recommended for production. This does require adding an external Zookeeper
cluster to provide bridge master selection and extra instances of the bridge and float. This allows
hot-warm operation of all the bridge and float instances. The Corda Enterprise node should be run as hot-cold HA too.
Highlighted in the diagram is the addition of the ``haConfig`` section to point at ``zookeeper`` and also the use of secondary
addresses in the ``alternateArtemisAddresses`` to allow node failover and in the ``floatAddresses`` to point at a
pool of DMZ float processes.:

.. image:: resources/bridge/ha_bridge_float.png
     :scale: 100%
     :align: center

