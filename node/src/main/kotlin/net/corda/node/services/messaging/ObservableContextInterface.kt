package net.corda.node.services.messaging

import net.corda.core.context.Trace
import net.corda.nodeapi.RPCApi
import org.apache.activemq.artemis.api.core.SimpleString
import java.util.concurrent.ConcurrentHashMap

interface ObservableContextInterface {
    fun sendMessage(serverToClient: RPCApi.ServerToClient)

    val observableMap: ObservableSubscriptionMap
    val clientAddressToObservables: ConcurrentHashMap<SimpleString, HashSet<Trace.InvocationId>>
    val deduplicationIdentity: String
    val clientAddress: SimpleString
}