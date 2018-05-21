package net.corda.node.services.messaging

import com.github.benmanes.caffeine.cache.Cache
import net.corda.core.context.Trace
import net.corda.nodeapi.RPCApi
import org.apache.activemq.artemis.api.core.SimpleString
import java.util.concurrent.ConcurrentHashMap

/**
 * An observable context is constructed on each RPC request. If subsequently a nested Observable is encountered this
 * same context is propagated by the serialization context. This way all observations rooted in a single RPC will be
 * muxed correctly. Note that the context construction itself is quite cheap.
 */
interface ObservableContextInterface {
    fun sendMessage(serverToClient: RPCApi.ServerToClient)

    val observableMap: Cache<Trace.InvocationId, ObservableSubscription>
    val clientAddressToObservables: ConcurrentHashMap<SimpleString, HashSet<Trace.InvocationId>>
    val deduplicationIdentity: String
    val clientAddress: SimpleString
}