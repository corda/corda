package net.corda.node.services.network

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.bufferUntilSubscribed
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.map
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.node.services.PartyInfo
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.node.services.api.NetworkCacheError
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.createMessage
import net.corda.node.services.messaging.sendRequest
import net.corda.node.services.network.NetworkMapService.FetchMapResponse
import net.corda.node.services.network.NetworkMapService.SubscribeResponse
import net.corda.node.utilities.AddOrRemove
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.wrapWithDatabaseTransaction
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.security.SignatureException
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * Extremely simple in-memory cache of the network map.
 *
 * @param serviceHub an optional service hub from which we'll take the identity service. We take a service hub rather
 * than the identity service directly, as this avoids problems with service start sequence (network map cache
 * and identity services depend on each other). Should always be provided except for unit test cases.
 */
@ThreadSafe
open class InMemoryNetworkMapCache(private val serviceHub: ServiceHub?) : SingletonSerializeAsToken(), NetworkMapCacheInternal {
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
    protected var registeredNodes: MutableMap<PublicKey, NodeInfo> = Collections.synchronizedMap(HashMap())

    override fun getPartyInfo(party: Party): PartyInfo? {
        val node = registeredNodes[party.owningKey]
        if (node != null) {
            return PartyInfo.Node(node)
        }
        for ((_, value) in registeredNodes) {
            for (service in value.advertisedServices) {
                if (service.identity.party == party) {
                    return PartyInfo.Service(service)
                }
            }
        }
        return null
    }

    override fun getNodeByLegalIdentityKey(identityKey: PublicKey): NodeInfo? = registeredNodes[identityKey]
    override fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo? {
        val wellKnownParty = if (serviceHub != null) {
            serviceHub.identityService.partyFromAnonymous(party)
        } else {
            party
        }

        return wellKnownParty?.let {
            getNodeByLegalIdentityKey(it.owningKey)
        }
    }

    override fun track(): Pair<List<NodeInfo>, Observable<MapChange>> {
        synchronized(_changed) {
            return Pair(partyNodes, _changed.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    override fun addMapService(network: MessagingService, networkMapAddress: SingleMessageRecipient, subscribe: Boolean,
                               ifChangedSinceVer: Int?): ListenableFuture<Unit> {
        if (subscribe && !registeredForPush) {
            // Add handler to the network, for updates received from the remote network map service.
            network.addMessageHandler(NetworkMapService.PUSH_TOPIC, DEFAULT_SESSION_ID) { message, _ ->
                try {
                    val req = message.data.deserialize<NetworkMapService.Update>()
                    val ackMessage = network.createMessage(NetworkMapService.PUSH_ACK_TOPIC, DEFAULT_SESSION_ID,
                            NetworkMapService.UpdateAcknowledge(req.mapVersion, network.myAddress).serialize().bytes)
                    network.send(ackMessage, req.replyTo)
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
        val req = NetworkMapService.FetchMapRequest(subscribe, ifChangedSinceVer, network.myAddress)
        val future = network.sendRequest<FetchMapResponse>(NetworkMapService.FETCH_TOPIC, req, networkMapAddress).map { (nodes) ->
            // We may not receive any nodes back, if the map hasn't changed since the version specified
            nodes?.forEach { processRegistration(it) }
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
    override fun deregisterForUpdates(network: MessagingService, service: NodeInfo): ListenableFuture<Unit> {
        // Fetch the network map and register for updates at the same time
        val req = NetworkMapService.SubscribeRequest(false, network.myAddress)
        val future = network.sendRequest<SubscribeResponse>(NetworkMapService.SUBSCRIPTION_TOPIC, req, service.address).map {
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
