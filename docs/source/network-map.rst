Network Map
===========

Protocol Design
---------------
The node info publishing protocol:

* Create a ``NodeInfo`` object, and sign it to create a ``SignedData<NodeInfo>`` object. TODO: We will need list of signatures in ``SignedData`` to support multiple node identities in the future.

* Serialise the signed data and POST the data to the network map server.

* The network map server validates the signature and acknowledges the registration with a HTTP 200 response, it will return HTTP 400 "Bad Request" if the data failed validation or if the public key wasn't registered with the network.

* The network map server will sign and distribute the new network map periodically.

Node side network map update protocol:

* The Corda node will query the network map service periodically according to the ``Expires`` attribute in the HTTP header.

* The network map service returns a signed ``NetworkMap`` object, containing list of node info hashes and the network parameters hashes.

* The node updates its local copy of ``NodeInfos`` if it is different from the newly downloaded ``NetworkMap``.

Network Map service REST API:

+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| Request method | Path                              | Description                                                                                                                                            |
+================+===================================+========================================================================================================================================================+
| POST           | /api/network-map/publish          | Publish new ``NodeInfo`` to the network map service, the legal identity in ``NodeInfo`` must match with the identity registered with the doorman.      |
+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /api/network-map                  | Retrieve ``NetworkMap`` from the server, the ``NetworkMap`` object contains list of node info hashes and NetworkParameters hash.                       |
+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /api/network-map/node-info/{hash} | Retrieve ``NodeInfo`` object with the same hash.                                                                                                  |
+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /api/network-map/parameters/{hash}| Retrieve ``NetworkParameters`` object with the same hash.                                                                                 |
+----------------+-----------------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------+

TODO: Access control of the network map will be added in the future.