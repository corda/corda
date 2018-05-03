package net.corda.node.services.messaging

import com.github.benmanes.caffeine.cache.Cache
import net.corda.core.context.Trace
import net.corda.nodeapi.RPCApi
import org.apache.activemq.artemis.api.core.SimpleString
import java.util.concurrent.ConcurrentHashMap

interface ObservableContextInterface {
    fun sendMessage(serverToClient: RPCApi.ServerToClient)

    val observableMap: Cache<Trace.InvocationId, ObservableSubscription>
    val clientAddressToObservables: ConcurrentHashMap<SimpleString, HashSet<Trace.InvocationId>>
    val deduplicationIdentity: String
    val clientAddress: SimpleString
}