Network Map
===========

Protocol Design
---------------
.. note:: This section is intended for developers who want to implement their own network map service.

The node info publishing protocol:

* Create a node info object, and sign it with node's private key to create a ``SignedData<NodeInfo>`` object.

* Serialise the signed data and POST the data to the network map.

* The network map server validate the signature and acknowledge the registration with a http 200 response, it will return http 400 "Bad Request" if the data failed validation or if the public key wasn't registered with the network.

* The network map server will distribute the new network map containing the new node info according to the network parameter ``eventHorizon`` attribute.


Node side network map update protocol:

* The Corda node will query the network map service periodically according to the network parameter.

* The network map service returns a signed ``NetworkMap`` object, containing list of node info hashes and the network parameter hashes.

* The node updates its local copy of NodeInfos and NetworkParameter if it is different from the newly downloaded NetworkMap.

Network Map service REST API:

+----------------+----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| Request method | Path                             | Description                                                                                                                                            |
+================+==================================+========================================================================================================================================================+
| POST           | /api/network-map/publish         | Publish new node info to the network map service                                                                                                       |
+----------------+----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /api/network-map                 | Retrieve ``NetworkMap`` from the server, the ``NetworkMap`` object contains list of node info hashes and NetworkParameter hash.                        |
+----------------+----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /api/network-map/node-info/{hash}| Retrieve ``NodeInfo`` object with the node info hash.                                                                                                  |
+----------------+----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /api/network-map/parameter/{hash}| Retrieve ``NetworkParameter`` object with the network parameter hash.                                                                                  |
+----------------+----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
