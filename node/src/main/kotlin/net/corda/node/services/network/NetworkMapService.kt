package net.corda.node.services.network

import net.corda.core.CordaException
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.ThreadBox
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.node.services.api.AbstractNodeService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.utilities.AddOrRemove
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe

/**
 * A network map contains lists of nodes on the network along with information about their identity keys, services
 * they provide and host names or IP addresses where they can be connected to. This information is cached locally within
 * nodes, by the [NetworkMapCache]. Currently very basic consensus controls are applied, using signed changes which
 * replace each other based on a serial number present in the change.
 */
// TODO: A better architecture for the network map service might be one like the Tor directory authorities, where
// several nodes linked by RAFT or Paxos elect a leader and that leader distributes signed documents describing the
// network layout. Those documents can then be cached by every node and thus a network map can/ be retrieved given only
// a single successful peer connection.
//
// It may also be that this is replaced or merged with the identity management service; for example if the network has
// a concept of identity changes over time, should that include the node for an identity? If so, that is likely to
// replace this service.
interface NetworkMapService {

}

@ThreadSafe
class InMemoryNetworkMapService(network: MessagingService): AbstractNetworkMapService(network) {

    override val nodeRegistrations: MutableMap<PartyAndCertificate, NodeRegistrationInfo> = ConcurrentHashMap()
    override val subscribers = ThreadBox(mutableMapOf<SingleMessageRecipient, LastAcknowledgeInfo>())
}

/**
 * Abstracted out core functionality as the basis for a persistent implementation, as well as existing in-memory implementation.
 *
 * Design is slightly refactored to track time and map version of last acknowledge per subscriber to facilitate
 * subscriber clean up and is simpler to persist than the previous implementation based on a set of missing messages acks.
 */
@ThreadSafe
abstract class AbstractNetworkMapService(network: MessagingService) : NetworkMapService, AbstractNodeService(network) {

    protected abstract val nodeRegistrations: MutableMap<PartyAndCertificate, NodeRegistrationInfo>

    // Map from subscriber address, to most recently acknowledged update map version.
    protected abstract val subscribers: ThreadBox<MutableMap<SingleMessageRecipient, LastAcknowledgeInfo>>
}

/**
 * A node registration state in the network map.
 *
 * @param node the node being added/removed.
 * @param serial an increasing value which represents the version of this registration. Not expected to be sequential,
 * but later versions of the registration must have higher values (or they will be ignored by the map service).
 * Similar to the serial number on DNS records.
 * @param type add if the node is being added to the map, or remove if a previous node is being removed (indicated as
 * going offline).
 * @param expires when the registration expires. Only used when adding a node to a map.
 */
// TODO: This might alternatively want to have a node and party, with the node being optional, so registering a node
// involves providing both node and paerty, and deregistering a node involves a request with party but no node.
@CordaSerializable
data class NodeRegistration(val node: NodeInfo, val serial: Long, val type: AddOrRemove, var expires: Instant) {
    /**
     * Build a node registration in wire format.
     */
    fun toWire(keyManager: KeyManagementService, publicKey: PublicKey): WireNodeRegistration {
        val regSerialized = this.serialize()
        val regSig = keyManager.sign(regSerialized.bytes, publicKey)

        return WireNodeRegistration(regSerialized, regSig)
    }

    override fun toString(): String = "$node #$serial ($type)"
}

/**
 * A node registration and its signature as a pair.
 */
@CordaSerializable
class WireNodeRegistration(raw: SerializedBytes<NodeRegistration>, sig: DigitalSignature.WithKey) : SignedData<NodeRegistration>(raw, sig) {
    @Throws(IllegalArgumentException::class)
    override fun verifyData(data: NodeRegistration) {
        // Check that the registration is fulfilled by any of node's identities.
        // TODO It may cause some problems with distributed services? We loose node's main identity. Should be all signatures instead of isFulfilledBy?
        require(data.node.legalIdentitiesAndCerts.any { it.owningKey.isFulfilledBy(sig.by) })
    }
}

@CordaSerializable
sealed class NodeMapException : CordaException("Network Map Protocol Error") {

    /** Thrown if the signature on the node info does not match the public key for the identity */
    class InvalidSignature : NodeMapException()

    /** Thrown if the replyTo of a subscription change message is not a single message recipient */
    class InvalidSubscriber : NodeMapException()
}

@CordaSerializable
data class LastAcknowledgeInfo(val mapVersion: Int)

@CordaSerializable
data class NodeRegistrationInfo(val reg: NodeRegistration, val mapVersion: Int)
