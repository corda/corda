API: RPC operations
===================
The node's owner interacts with the node solely via remote procedure calls (RPC). The node's owner does not have
access to the node's ``ServiceHub``.

The key RPC operations exposed by the node are:

* ``CordaRPCOps.vaultQueryBy/CordaRPCOps.vaultTrackBy``
    *
* ``CordaRPCOps.verifiedTransactions``
    *
* ``CordaRPCOps.networkMapUpdates``
    *
* ``CordaRPCOps.startFlowDynamic/CordaRPCOps.startTrackedFlowDynamic``
    *
* ``CordaRPCOps.nodeIdentity``
    *
* ``CordaRPCOps.currentNodeTime``
    *
* ``CordaRPCOps.partyFromKey/CordaRPCOps.partyFromX500Name``
    *
* ``CordaRPCOps.attachmentExists``/``CordaRPCOps.openAttachment``/``CordaRPCOps.uploadAttachment``
    *
* ``CordaRPCOps.registeredFlows``
    *
