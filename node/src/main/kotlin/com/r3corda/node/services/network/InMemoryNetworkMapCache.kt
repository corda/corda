package com.r3corda.node.services.network

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.contracts.Contract
import com.r3corda.core.crypto.Party
import com.r3corda.core.map
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.messaging.createMessage
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.node.services.NetworkCacheError
import com.r3corda.core.node.services.NetworkMapCache
import com.r3corda.core.node.services.NetworkMapCache.MapChange
import com.r3corda.core.node.services.NetworkMapCache.MapChangeType
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.node.services.network.NetworkMapService.Companion.FETCH_PROTOCOL_TOPIC
import com.r3corda.node.services.network.NetworkMapService.Companion.SUBSCRIPTION_PROTOCOL_TOPIC
import com.r3corda.node.services.network.NetworkMapService.FetchMapResponse
import com.r3corda.node.services.network.NetworkMapService.SubscribeResponse
import com.r3corda.node.utilities.AddOrRemove
import com.r3corda.protocols.sendRequest
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
        get() = get(NetworkMapService.type)
    override val regulators: List<NodeInfo>
        get() = get(ServiceType.regulator)
    override val notaryNodes: List<NodeInfo>
        get() = get(ServiceType.notary)
    override val partyNodes: List<NodeInfo>
        get() = registeredNodes.map { it.value }
    private val _changed = PublishSubject.create<MapChange>()
    override val changed: Observable<MapChange> = _changed
    private val _registrationFuture = SettableFuture.create<Unit>()
    override val mapServiceRegistered: ListenableFuture<Unit>
        get() = _registrationFuture

    private var registeredForPush = false
    protected var registeredNodes = Collections.synchronizedMap(HashMap<Party, NodeInfo>())

    override fun get() = registeredNodes.map { it.value }
    override fun get(serviceType: ServiceType) = registeredNodes.filterValues { it.advertisedServices.any { it.info.type.isSubTypeOf(serviceType) } }.map { it.value }
    override fun getRecommended(type: ServiceType, contract: Contract, vararg party: Party): NodeInfo? = get(type).firstOrNull()
    override fun getNodeByLegalName(name: String) = get().singleOrNull { it.legalIdentity.name == name }
    override fun getNodeByPublicKey(publicKey: PublicKey) = get().singleOrNull {
        (it.legalIdentity.owningKey == publicKey)
            || it.advertisedServices.any { it.identity.owningKey == publicKey }
    }

    override fun addMapService(net: MessagingService, networkMapAddress: SingleMessageRecipient, subscribe: Boolean,
                               ifChangedSinceVer: Int?): ListenableFuture<Unit> {
        if (subscribe && !registeredForPush) {
            // Add handler to the network, for updates received from the remote network map service.
            net.addMessageHandler(NetworkMapService.PUSH_PROTOCOL_TOPIC, DEFAULT_SESSION_ID) { message, r ->
                try {
                    val req = message.data.deserialize<NetworkMapService.Update>()
                    val ackMessage = net.createMessage(NetworkMapService.PUSH_ACK_PROTOCOL_TOPIC, DEFAULT_SESSION_ID,
                            NetworkMapService.UpdateAcknowledge(req.mapVersion, net.myAddress).serialize().bits)
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
        val req = NetworkMapService.FetchMapRequest(subscribe, ifChangedSinceVer, net.myAddress)
        val future = net.sendRequest<FetchMapResponse>(FETCH_PROTOCOL_TOPIC, req, networkMapAddress).map { resp ->
            // We may not receive any nodes back, if the map hasn't changed since the version specified
            resp.nodes?.forEach { processRegistration(it) }
            Unit
        }
        _registrationFuture.setFuture(future)

        return future
    }

    override fun addNode(node: NodeInfo) {
        val oldValue = registeredNodes.put(node.legalIdentity, node)
        if (oldValue == null) {
            _changed.onNext(MapChange(node, oldValue, MapChangeType.Added))
        } else if(oldValue != node) {
            _changed.onNext(MapChange(node, oldValue, MapChangeType.Modified))
        }
    }

    override fun removeNode(node: NodeInfo) {
        val oldValue = registeredNodes.remove(node.legalIdentity)
        _changed.onNext(MapChange(node, oldValue, MapChangeType.Removed))
    }

    /**
     * Unsubscribes from updates from the given map service.
     *
     * @param service the network map service to listen to updates from.
     */
    override fun deregisterForUpdates(net: MessagingService, service: NodeInfo): ListenableFuture<Unit> {
        // Fetch the network map and register for updates at the same time
        val req = NetworkMapService.SubscribeRequest(false, net.myAddress)
        val future = net.sendRequest<SubscribeResponse>(SUBSCRIPTION_PROTOCOL_TOPIC, req, service.address).map {
            if (it.confirmed) Unit else throw NetworkCacheError.DeregistrationFailed()
        }
        _registrationFuture.setFuture(future)
        return future
    }

    fun processUpdatePush(req: NetworkMapService.Update) {
        try {
            val reg = req.wireReg.verified()
            processRegistration(reg)
        } catch (e: SignatureException) {
            throw NodeMapError.InvalidSignature()
        }
    }

    private fun processRegistration(reg: NodeRegistration) {
        // TODO: Implement filtering by sequence number, so we only accept changes that are
        // more recent than the latest change we've processed.
        when (reg.type) {
            AddOrRemove.ADD -> addNode(reg.node)
            AddOrRemove.REMOVE -> removeNode(reg.node)
        }
    }

    @VisibleForTesting
    override fun runWithoutMapService() {
        _registrationFuture.set(Unit)
    }
}