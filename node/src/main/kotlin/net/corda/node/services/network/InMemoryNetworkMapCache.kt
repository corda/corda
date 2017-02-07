package net.corda.node.services.network

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.bufferUntilSubscribed
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.map
import net.corda.core.messaging.MessagingService
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.messaging.createMessage
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.node.services.NetworkCacheError
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.node.services.PartyInfo
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.flows.sendRequest
import net.corda.node.services.network.NetworkMapService.Companion.FETCH_FLOW_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.SUBSCRIPTION_FLOW_TOPIC
import net.corda.node.services.network.NetworkMapService.FetchMapResponse
import net.corda.node.services.network.NetworkMapService.SubscribeResponse
import net.corda.node.utilities.AddOrRemove
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.wrapWithDatabaseTransaction
import rx.Observable
import rx.subjects.PublishSubject
import java.security.SignatureException
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * Extremely simple in-memory cache of the network map.
 */
@ThreadSafe
open class InMemoryNetworkMapCache : SingletonSerializeAsToken(), NetworkMapCache {
    companion object {
        val logger = loggerFor<InMemoryNetworkMapCache>()
    }

    override val partyNodes: List<NodeInfo> get() = registeredNodes.map { it.value }
    override val networkMapNodes: List<NodeInfo> get() = getNodesWithService(NetworkMapService.type)
    private val _changed = PublishSubject.create<MapChange>()
    // We use assignment here so that multiple subscribers share the same wrapped Observable.
    override val changed: Observable<MapChange> = _changed.wrapWithDatabaseTransaction()
    private val changePublisher: rx.Observer<MapChange> get() = _changed.bufferUntilDatabaseCommit()

    private val _registrationFuture = SettableFuture.create<Unit>()
    override val mapServiceRegistered: ListenableFuture<Unit> get() = _registrationFuture

    private var registeredForPush = false
    protected var registeredNodes: MutableMap<CompositeKey, NodeInfo> = Collections.synchronizedMap(HashMap())

    override fun getPartyInfo(party: Party): PartyInfo? {
        val node = registeredNodes[party.owningKey]
        if (node != null) {
            return PartyInfo.Node(node)
        }
        for (entry in registeredNodes) {
            for (service in entry.value.advertisedServices) {
                if (service.identity == party) {
                    return PartyInfo.Service(service)
                }
            }
        }
        return null
    }

    override fun getNodeByLegalIdentityKey(compositeKey: CompositeKey): NodeInfo? = registeredNodes[compositeKey]

    override fun track(): Pair<List<NodeInfo>, Observable<MapChange>> {
        synchronized(_changed) {
            return Pair(partyNodes, _changed.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    override fun addMapService(net: MessagingService, networkMapAddress: SingleMessageRecipient, subscribe: Boolean,
                               ifChangedSinceVer: Int?): ListenableFuture<Unit> {
        if (subscribe && !registeredForPush) {
            // Add handler to the network, for updates received from the remote network map service.
            net.addMessageHandler(NetworkMapService.PUSH_FLOW_TOPIC, DEFAULT_SESSION_ID) { message, r ->
                try {
                    val req = message.data.deserialize<NetworkMapService.Update>()
                    val ackMessage = net.createMessage(NetworkMapService.PUSH_ACK_FLOW_TOPIC, DEFAULT_SESSION_ID,
                            NetworkMapService.UpdateAcknowledge(req.mapVersion, net.myAddress).serialize().bytes)
                    net.send(ackMessage, req.replyTo)
                    processUpdatePush(req)
                } catch(e: NodeMapError) {
                    logger.warn("Failure during node map update due to bad update: ${e.javaClass.name}")
                } catch(e: Exception) {
                    logger.error("Exception processing update from network map service", e)
                }
            }
            registeredForPush = true
        }

        // Fetch the network map and register for updates at the same time
        val req = NetworkMapService.FetchMapRequest(subscribe, ifChangedSinceVer, net.myAddress)
        val future = net.sendRequest<FetchMapResponse>(FETCH_FLOW_TOPIC, req, networkMapAddress).map { resp ->
            // We may not receive any nodes back, if the map hasn't changed since the version specified
            resp.nodes?.forEach { processRegistration(it) }
            Unit
        }
        _registrationFuture.setFuture(future)

        return future
    }

    override fun addNode(node: NodeInfo) {
        synchronized(_changed) {
            val previousNode = registeredNodes.put(node.legalIdentity.owningKey, node)
            if (previousNode == null) {
                changePublisher.onNext(MapChange.Added(node))
            } else if (previousNode != node) {
                changePublisher.onNext(MapChange.Modified(node, previousNode))
            }
        }
    }

    override fun removeNode(node: NodeInfo) {
        synchronized(_changed) {
            registeredNodes.remove(node.legalIdentity.owningKey)
            changePublisher.onNext(MapChange.Removed(node))
        }
    }

    /**
     * Unsubscribes from updates from the given map service.
     * @param service the network map service to listen to updates from.
     */
    override fun deregisterForUpdates(net: MessagingService, service: NodeInfo): ListenableFuture<Unit> {
        // Fetch the network map and register for updates at the same time
        val req = NetworkMapService.SubscribeRequest(false, net.myAddress)
        val future = net.sendRequest<SubscribeResponse>(SUBSCRIPTION_FLOW_TOPIC, req, service.address).map {
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
