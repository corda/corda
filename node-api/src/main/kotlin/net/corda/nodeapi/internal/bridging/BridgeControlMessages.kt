package net.corda.nodeapi.internal.bridging

import net.corda.annotations.serialization.Serializable
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort

/**
 * The information required to construct a bridge to a remote peer.
 * @property queueName The local source queue from which to move messages.
 * @property targets The list of TCP connection targets on which the peer resides
 * @property legalNames The list of acceptable [CordaX500Name] names that should be presented as subject of the validated peer TLS certificate.
 */
@Serializable
data class BridgeEntry(val queueName: String, val targets: List<NetworkHostAndPort>, val legalNames: List<CordaX500Name>)

sealed class BridgeControl {
    /**
     * This message is sent on node start to inform any bridges of valid inbound peer-to-peer topics and pre-existing outbound queues needing bridging.
     * @property nodeIdentity This is used for informational purposes to identify the originating node instance.
     * @property inboxQueues The list of P2P inbox queue names/addresses, which could be used to filter inbound messages and prevent any identity spoofing.
     * @property sendQueues The list [BridgeEntry] for all pre-existing local queues requiring a bridge to a remote peer.
     */
    @Serializable
    data class NodeToBridgeSnapshot(val nodeIdentity: String, val inboxQueues: List<String>, val sendQueues: List<BridgeEntry>) : BridgeControl()

    /**
     * This message is sent on bridge start to re-request NodeToBridgeSnapshot information from all nodes on the broker.
     * @property bridgeIdentity This is used for informational purposes to identify the originating bridge instance.
     */
    @Serializable
    data class BridgeToNodeSnapshotRequest(val bridgeIdentity: String) : BridgeControl()

    /**
     * This message is sent to any active bridges to create a new bridge if one does not already exist. It may also be sent if updated
     * information arrives from the network map to allow connection details of a pre-existing queue to now be resolved.
     * @property nodeIdentity This is used for informational purposes to identify the originating node instance.
     * @property bridgeInfo The connection details of the new bridge.
     */
    @Serializable
    data class Create(val nodeIdentity: String, val bridgeInfo: BridgeEntry) : BridgeControl()

    /**
     * This message is sent to any active bridges to tear down an existing bridge. Typically this is only done when there is a change in network map details for a peer.
     * The source queue is not affected by this operation and it is the responsibility of the node to ensure there are no unsent messages and to delete the durable queue.
     * @property nodeIdentity This is used for informational purposes to identify the originating node instance.
     * @property bridgeInfo The connection details of the bridge to be removed
     */
    @Serializable
    data class Delete(val nodeIdentity: String, val bridgeInfo: BridgeEntry) : BridgeControl()
}