package net.corda.node.services.network

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.bufferUntilSubscribed
import net.corda.core.contracts.Contract
import net.corda.core.crypto.Party
import net.corda.core.crypto.PublicKeyTree
import net.corda.core.map
import net.corda.core.messaging.MessagingService
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.messaging.createMessage
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.node.services.NetworkCacheError
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.node.services.NetworkMapCache.MapChangeType
import net.corda.core.node.services.ServiceType
import net.corda.core.randomOrNull
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.services.network.NetworkMapService.Companion.FETCH_PROTOCOL_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.SUBSCRIPTION_PROTOCOL_TOPIC
import net.corda.node.services.network.NetworkMapService.FetchMapResponse
import net.corda.node.services.network.NetworkMapService.SubscribeResponse
import net.corda.node.utilities.AddOrRemove
import net.corda.protocols.sendRequest
import rx.Observable
import rx.subjects.PublishSubject
import java.security.SignatureException
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * Extremely simple in-memory cache of the network map.
 *
 * TODO: some method implementations can be moved up to [NetworkMapCache]
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

    override fun track(): Pair<List<NodeInfo>, Observable<MapChange>> {
        synchronized(_changed) {
            return Pair(partyNodes, _changed.bufferUntilSubscribed())
        }
    }

    override fun get() = registeredNodes.map { it.value }
    override fun get(serviceType: ServiceType) = registeredNodes.filterValues { it.advertisedServices.any { it.info.type.isSubTypeOf(serviceType) } }.map { it.value }
    override fun getRecommended(type: ServiceType, contract: Contract, vararg party: Party): NodeInfo? = get(type).firstOrNull()
    override fun getNodeByLegalName(name: String) = get().singleOrNull { it.legalIdentity.name == name }
    override fun getNodeByPublicKeyTree(publicKeyTree: PublicKeyTree): NodeInfo? {
        // Although we should never have more than one match, it is theoretically possible. Report an error if it happens.
        val candidates = get().filter {
            (it.legalIdentity.owningKey == publicKeyTree)
                    || it.advertisedServices.any { it.identity.owningKey == publicKeyTree }
        }
        if (candidates.size > 1) {
            throw IllegalStateException("Found more than one match for key $publicKeyTree")
        }
        return candidates.singleOrNull()
    }

    override fun getRepresentativeNode(party: Party): NodeInfo? {
        return partyNodes.randomOrNull { it.legalIdentity == party || it.advertisedServices.any { it.identity == party } }
    }

    override fun getNotary(name: String): Party? {
        val notaryNode = notaryNodes.randomOrNull { it.advertisedServices.any { it.info.type.isSubTypeOf(ServiceType.notary) && it.info.name == name } }
        return notaryNode?.notaryIdentity
    }

    override fun getAnyNotary(type: ServiceType?): Party? {
        val nodes = if (type == null) {
            notaryNodes
        } else {
            require(type != ServiceType.notary && type.isSubTypeOf(ServiceType.notary)) { "The provided type must be a specific notary sub-type" }
            notaryNodes.filter { it.advertisedServices.any { it.info.type == type } }
        }

        return nodes.randomOrNull()?.notaryIdentity
    }

    override fun isNotary(party: Party) = notaryNodes.any { it.notaryIdentity == party }

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
        synchronized(_changed) {
            val oldValue = registeredNodes.put(node.legalIdentity, node)
            if (oldValue == null) {
                _changed.onNext(MapChange(node, oldValue, MapChangeType.Added))
            } else if (oldValue != node) {
                _changed.onNext(MapChange(node, oldValue, MapChangeType.Modified))
            }
        }

    }

    override fun removeNode(node: NodeInfo) {
        synchronized(_changed) {
            val oldValue = registeredNodes.remove(node.legalIdentity)
            _changed.onNext(MapChange(node, oldValue, MapChangeType.Removed))
        }
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
