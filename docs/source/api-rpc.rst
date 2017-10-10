API: RPC operations
===================
The node's owner interacts with the node solely via remote procedure calls (RPC). The node's owner does not have
access to the node's ``ServiceHub``.

The key RPC operations exposed by the node are:

* ``CordaRPCOps.vaultQueryBy``
    * Extract states from the node's vault based on a query criteria
* ``CordaRPCOps.vaultTrackBy``
    * As above, but also returns an observable of future states matching the query
* ``CordaRPCOps.networkMapFeed``
    * A list of network nodes, and an observable of changes to the network map
* ``CordaRPCOps.registeredFlows``
    * See a list of registered flows on the node
* ``CordaRPCOps.startFlowDynamic``
    * Start one of the node's registered flows
* ``CordaRPCOps.startTrackedFlowDynamic``
    * As above, but also returns a progress handle for the flow
* ``CordaRPCOps.nodeInfo``
    * Returns information about the node
* ``CordaRPCOps.currentNodeTime``
    * Returns the current time according to the node's clock
* ``CordaRPCOps.partyFromKey/CordaRPCOps.wellKnownPartyFromX500Name``
    * Retrieves a party on the network based on a public key or X500 name
* ``CordaRPCOps.uploadAttachment``/``CordaRPCOps.openAttachment``/``CordaRPCOps.attachmentExists``
    * Uploads, opens and checks for the existence of attachments