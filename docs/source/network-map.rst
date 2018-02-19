Network Map
===========

The network map is a collection of signed ``NodeInfo`` objects (signed by the node it represents and thus tamper-proof)
forming the set of reachable nodes in a compatibility zone. A node can receive these objects from two sources:

1. The HTTP network map service if the ``compatibilityZoneURL`` config key is specified.
2. The ``additional-node-infos`` directory within the node's directory.

HTTP network map service
------------------------

If the node is configured with the ``compatibilityZoneURL`` config then it first uploads its own signed ``NodeInfo``
to the server (and each time it changes on startup) and then proceeds to download the entire network map. The network map
consists of a list of ``NodeInfo`` hashes. The node periodically polls for the network map (based on the HTTP cache expiry
header) and any new entries are downloaded and cached. Entries which no longer exist are deleted from the node's cache.

The set of REST end-points for the network map service are as follows.

+----------------+-----------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------+
| Request method | Path                                    | Description                                                                                                                                  |
+================+=========================================+==============================================================================================================================================+
| POST           | /network-map/publish                    | For the node to upload its signed ``NodeInfo`` object to the network map.                                                                    |
+----------------+-----------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------+
| POST           | /network-map/ack-parameters             | For the node operator to acknowledge network map that new parameters were accepted for future update.                                        |
+----------------+-----------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /network-map                            | Retrieve the current signed network map object. The entire object is signed with the network map certificate which is also attached.         |
+----------------+-----------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /network-map/node-info/{hash}           | Retrieve a signed ``NodeInfo`` as specified in the network map object.                                                                       |
+----------------+-----------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /network-map/network-parameters/{hash}  | Retrieve the signed network parameters (see below). The entire object is signed with the network map certificate which is also attached.     |
+----------------+-----------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------+


The ``additional-node-infos`` directory
---------------------------------------

Alongside the HTTP network map service, or as a replacement if the node isn't connected to one, the node polls the
contents of the ``additional-node-infos`` directory located in its base directory. Each file is expected to be the same
signed ``NodeInfo`` object that the network map service vends. These are automatically added to the node's cache and can
be used to supplement or replace the HTTP network map. If the same node is advertised through both mechanisms then the
latest one is taken.

On startup the node generates its own signed node info file, filename of the format ``nodeInfo-${hash}``. To create a simple
network without the HTTP network map service then simply place this file in the ``additional-node-infos`` directory
of every node that's part of this network.

Network parameters
------------------

Network parameters are a set of values that every node participating in the zone needs to agree on and use to
correctly interoperate with each other. If the node is using the HTTP network map service then on first startup it will
download the signed network parameters, cache it in a ``network-parameters`` file and apply them on the node.

.. warning:: If the ``network-parameters`` file is changed and no longer matches what the network map service is advertising
  then the node will automatically shutdown. Resolution to this is to delete the incorrect file and restart the node so
  that the parameters can be downloaded again.

.. note:: A future release will support the notion of phased rollout of network parameter changes.

If the node isn't using a HTTP network map service then it's expected the signed file is provided by some other means.
For such a scenario there is the network bootstrapper tool which in addition to generating the network parameters file
also distributes the node info files to the node directories. More information can be found in :doc:`setting-up-a-corda-network`.

The current set of network parameters:

:minimumPlatformVersion: The minimum platform version that the nodes must be running. Any node which is below this will
        not start.
:notaries: List of identity and validation type (either validating or non-validating) of the notaries which are permitted
        in the compatibility zone.
:maxMessageSize: Maximum allowed size in bytes of an individual message sent over the wire. Note that attachments are
        a special case and may be fragmented for streaming transfer, however, an individual transaction or flow message
        may not be larger than this value.
:maxTransactionSize: Maximum allowed size in bytes of a transaction. This is the size of the transaction object and its attachments.
:modifiedTime: The time when the network parameters were last modified by the compatibility zone operator.
:epoch: Version number of the network parameters. Starting from 1, this will always increment whenever any of the
        parameters change.

More parameters will be added in future releases to regulate things like allowed port numbers, how long a node can be
offline before it is evicted from the zone, whether or not IPv6 connectivity is required for zone members, required
cryptographic algorithms and rollout schedules (e.g. for moving to post quantum cryptography), parameters related to
SGX and so on.

Network parameters update process
---------------------------------

In case of the need to change network parameters Corda zone operator will start the update process. There are many reasons
that may lead to this decision: we discovered that some new fields have to be added to enable smooth network interoperability or change
of the existing compatibility constants is required due to upgrade or security reasons.

To synchronize all nodes in the compatibility zone to use the new set of the network parameters two RPC methods exist. The process
requires human interaction and approval of the change.

When the update is about to happen the network map service starts to advertise the additional information with the usual network map
data. It includes new network parameters hash, description of the change and the update deadline. Node queries network map server
for the new set of parameters and emits ``ParametersUpdateInfo`` via ``CordaRPCOps::networkParametersFeed`` method to inform
node operator about the event.

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/messaging/CordaRPCOps.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

Node administrator can review the change and decide if is going to accept it. The approval should be done before ``updateDeadline``.
Nodes that don't approve before the deadline will be removed from the network map.
If the network operator starts advertising a different set of new parameters then that new set overrides the previous set. Only the latest update can be accepted.
To send back parameters approval to the zone operator RPC method ``fun acceptNewNetworkParameters(parametersHash: SecureHash)``
has to be called with ``parametersHash`` from update. Notice that the process cannot be undone.

Next time the node polls network map after the deadline the advertised network parameters will be the updated ones. Previous set
of parameters will no longer be valid. At this point the node will automatically shutdown and will require the node operator
to bring it back again.
