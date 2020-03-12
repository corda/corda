package net.corda.nodeapi.internal.serialization.testutils

import com.github.benmanes.caffeine.cache.Cache
import net.corda.core.context.Trace
import net.corda.nodeapi.internal.rpc.ObservableContextInterface
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.rpc.ObservableSubscription
import org.apache.activemq.artemis.api.core.SimpleString
import java.util.concurrent.ConcurrentHashMap

class TestObservableContext(
        override val observableMap: Cache<Trace.InvocationId, ObservableSubscription>,
        override val clientAddressToObservables: ConcurrentHashMap<SimpleString, HashSet<Trace.InvocationId>>,
        override val deduplicationIdentity: String,
        override val clientAddress: SimpleString
) : ObservableContextInterface {
    override fun sendMessage(serverToClient: RPCApi.ServerToClient) { }
}