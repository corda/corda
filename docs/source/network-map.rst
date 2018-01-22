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
:modifiedTime: The time when the network parameters were last modified by the compatibility zone operator.
:epoch: Version number of the network parameters. Starting from 1, this will always increment whenever any of the
        parameters change.

More parameters will be added in future releases to regulate things like allowed port numbers, how long a node can be
offline before it is evicted from the zone, whether or not IPv6 connectivity is required for zone members, required
cryptographic algorithms and rollout schedules (e.g. for moving to post quantum cryptography), parameters related to
SGX and so on.
