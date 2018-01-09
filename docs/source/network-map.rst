Network Map
===========

The network map stores a collection of ``NodeInfo`` objects, each representing another node with which the node can interact.
There are two sources from which a Corda node can retrieve ``NodeInfo`` objects:

1. the REST protocol with the network map service, which also provides a publishing API,

2. the ``additional-node-infos`` directory.


Protocol Design
---------------
The node info publishing protocol:

* Create a ``NodeInfo`` object, and sign it to create a ``SignedNodeInfo`` object.

* Serialise the signed data and POST the data to the network map server.

* The network map server validates the signature and acknowledges the registration with a HTTP 200 response, it will return HTTP 400 "Bad Request" if the data failed validation or if the public key wasn't registered with the network.

* The network map server will sign and distribute the new network map periodically.

Node side network map update protocol:

* The Corda node will query the network map service periodically according to the ``Expires`` attribute in the HTTP header.

* The network map service returns a signed ``NetworkMap`` object which looks as follows:

.. container:: codeset

   .. sourcecode:: kotlin

        data class NetworkMap {
            val nodeInfoHashes: List<SecureHash>,
            val networkParametersHash: SecureHash
        }

The object contains list of node info hashes and hash of the network parameters data structure (without the signatures).

* The node updates its local copy of ``NodeInfos`` if it is different from the newly downloaded ``NetworkMap``.

Network Map service REST API:

+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| Request method | Path                              | Description                                                                                                                                            |
+================+===================================+========================================================================================================================================================+
| POST           | /network-map/publish              | Publish new ``NodeInfo`` to the network map service, the legal identity in ``NodeInfo`` must match with the identity registered with the doorman.      |
+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /network-map                      | Retrieve ``NetworkMap`` from the server, the ``NetworkMap`` object contains list of node info hashes and ``NetworkParameters`` hash.                   |
+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /network-map/node-info/{hash}     | Retrieve ``NodeInfo`` object with the same hash.                                                                                                       |
+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /network-map/parameters/{hash}    | Retrieve ``NetworkParameters`` object with the same hash.                                                                                              |
+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+

TODO: Access control of the network map will be added in the future.


The ``additional-node-infos`` directory
---------------------------------------
Each Corda node reads, and continuously polls, the files contained in a directory named ``additional-node-infos`` inside the node base directory.

Nodes expect to find a serialized ``SignedNodeInfo`` object, the same object which is sent to network map server.

Whenever a node starts it writes on disk a file containing its own ``NodeInfo``, this file is called ``nodeInfo-XXX`` where ``XXX`` is a long string.

Hence if an operator wants node A to see node B they can pick B's ``NodeInfo`` file from B base directory and drop it into A's ``additional-node-infos`` directory.


Network parameters
------------------
Network parameters are constants that every node participating in the network needs to agree on and use for interop purposes.
The structure is distributed as a file containing serialized ``SignedData<NetworkParameters>`` with a signature from
a sub-key of the compatibility zone root cert. Network map advertises the hash of currently used network parameters.
The ``NetworkParameters`` structure contains:
 * ``minimumPlatformVersion`` -  minimum version of Corda platform that is required for nodes in the network.
 * ``notaries`` - list of well known and trusted notary identities with information on validation type.
 * ``maxMessageSize`` - maximum P2P message size sent over the wire in bytes.
 * ``maxTransactionSize`` - maximum permitted transaction size in bytes.
 * ``modifiedTime`` - the time the network parameters were created by the CZ operator.
 * ``epoch`` - version number of the network parameters. Starting from 1, this will always increment on each new set of parameters.

The set of parameters is still under development and we may find the need to add additional fields.
