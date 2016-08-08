package com.r3corda.node.services.network

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.contracts.Contract
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.runOnNextMessage
import com.r3corda.core.messaging.send
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.node.services.NetworkCacheError
import com.r3corda.core.node.services.NetworkMapCache
import com.r3corda.core.node.services.NetworkMapCache.MapChange
import com.r3corda.core.node.services.NetworkMapCache.MapChangeType
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.node.services.api.RegulatorService
import com.r3corda.node.services.clientapi.NodeInterestRates
import com.r3corda.node.services.transactions.NotaryService
import com.r3corda.node.utilities.AddOrRemove
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.security.SignatureException
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * Extremely simple in-memory cache of the network map.
 */
@ThreadSafe
open class InMemoryNetworkMapCache : SingletonSerializeAsToken(), NetworkMapCache {
    override val networkMapNodes: List<NodeInfo>
        get() = get(NetworkMapService.Type)
    override val regulators: List<NodeInfo>
        get() = get(RegulatorService.Type)
    override val notaryNodes: List<NodeInfo>
        get() = get(NotaryService.Type)
    override val ratesOracleNodes: List<NodeInfo>
        get() = get(NodeInterestRates.Type)
    override val partyNodes: List<NodeInfo>
        get() = registeredNodes.map { it.value }
    private val _changed = PublishSubject.create<MapChange>()
    override val changed: Observable<MapChange> = _changed

    private var registeredForPush = false
    protected var registeredNodes = Collections.synchronizedMap(HashMap<Party, NodeInfo>())

    override fun get() = registeredNodes.map { it.value }
    override fun get(serviceType: ServiceType) = registeredNodes.filterValues { it.advertisedServices.any { it.isSubTypeOf(serviceType) } }.map { it.value }
    override fun getRecommended(type: ServiceType, contract: Contract, vararg party: Party): NodeInfo? = get(type).firstOrNull()
    override fun getNodeByLegalName(name: String) = get().singleOrNull { it.identity.name == name }
    override fun getNodeByPublicKey(publicKey: PublicKey) = get().singleOrNull { it.identity.owningKey == publicKey }

    override fun addMapService(net: MessagingService, service: NodeInfo, subscribe: Boolean,
                               ifChangedSinceVer: Int?): ListenableFuture<Unit> {
        if (subscribe && !registeredForPush) {
            // Add handler to the network, for updates received from the remote network map service.
            net.addMessageHandler(NetworkMapService.PUSH_PROTOCOL_TOPIC, DEFAULT_SESSION_ID, null) { message, r ->
                try {
                    val req = message.data.deserialize<NetworkMapService.Update>()
                    val hash = SecureHash.sha256(req.wireReg.serialize().bits)
                    val ackMessage = net.createMessage(NetworkMapService.PUSH_ACK_PROTOCOL_TOPIC, DEFAULT_SESSION_ID,
                            NetworkMapService.UpdateAcknowledge(hash, net.myAddress).serialize().bits)
                    net.send(ackMessage, req.replyTo)
                    processUpdatePush(req)
                } catch(e: NodeMapError) {
                    NetworkMapCache.logger.warn("Failure during node map update due to bad update: ${e.javaClass.name}")
                } catch(e: Exception) {
                    NetworkMapCache.logger.error("Exception processing update from network map service", e)
                }
            }
            registeredForPush = true
        }

        // Fetch the network map and register for updates at the same time
        val sessionID = random63BitValue()
        val req = NetworkMapService.FetchMapRequest(subscribe, ifChangedSinceVer, net.myAddress, sessionID)

        // Add a message handler for the response, and prepare a future to put the data into.
        // Note that the message handler will run on the network thread (not this one).
        val future = SettableFuture.create<Unit>()
        net.runOnNextMessage(NetworkMapService.FETCH_PROTOCOL_TOPIC, sessionID, MoreExecutors.directExecutor()) { message ->
            val resp = message.data.deserialize<NetworkMapService.FetchMapResponse>()
            // We may not receive any nodes back, if the map hasn't changed since the version specified
            resp.nodes?.forEach { processRegistration(it) }
            future.set(Unit)
        }
        net.send(NetworkMapService.FETCH_PROTOCOL_TOPIC, DEFAULT_SESSION_ID, req, service.address)

        return future
    }

    override fun addNode(node: NodeInfo) {
        registeredNodes[node.identity] = node
        _changed.onNext(MapChange(node, MapChangeType.Added))
    }

    override fun removeNode(node: NodeInfo) {
        registeredNodes.remove(node.identity)
        _changed.onNext(MapChange(node, MapChangeType.Removed))
    }

    /**
     * Unsubscribes from updates from the given map service.
     *
     * @param service the network map service to listen to updates from.
     */
    override fun deregisterForUpdates(net: MessagingService, service: NodeInfo): ListenableFuture<Unit> {
        // Fetch the network map and register for updates at the same time
        val sessionID = random63BitValue()
        val req = NetworkMapService.SubscribeRequest(false, net.myAddress, sessionID)

        // Add a message handler for the response, and prepare a future to put the data into.
        // Note that the message handler will run on the network thread (not this one).
        val future = SettableFuture.create<Unit>()
        net.runOnNextMessage(NetworkMapService.SUBSCRIPTION_PROTOCOL_TOPIC, sessionID, MoreExecutors.directExecutor()) { message ->
            val resp = message.data.deserialize<NetworkMapService.SubscribeResponse>()
            if (resp.confirmed) {
                future.set(Unit)
            } else {
                future.setException(NetworkCacheError.DeregistrationFailed())
            }
        }
        net.send(NetworkMapService.SUBSCRIPTION_PROTOCOL_TOPIC, DEFAULT_SESSION_ID, req, service.address)

        return future
    }

    fun processUpdatePush(req: NetworkMapService.Update) {
        val reg: NodeRegistration
        try {
            reg = req.wireReg.verified()
        } catch(e: SignatureException) {
            throw NodeMapError.InvalidSignature()
        }
        processRegistration(reg)
    }

    private fun processRegistration(reg: NodeRegistration) {
        // TODO: Implement filtering by sequence number, so we only accept changes that are
        // more recent than the latest change we've processed.
        when (reg.type) {
            AddOrRemove.ADD -> addNode(reg.node)
            AddOrRemove.REMOVE -> removeNode(reg.node)
        }
    }
}