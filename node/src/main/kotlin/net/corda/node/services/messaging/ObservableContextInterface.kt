package net.corda.node.services.messaging

import com.google.common.collect.SetMultimap
import net.corda.core.context.Trace
import net.corda.nodeapi.RPCApi
import org.apache.activemq.artemis.api.core.SimpleString

interface ObservableContextInterface {
    fun sendMessage(serverToClient: RPCApi.ServerToClient)

    val observableMap: ObservableSubscriptionMap
    val clientAddressToObservables: SetMultimap<SimpleString, Trace.InvocationId>
    val deduplicationIdentity: String
    val clientAddress: SimpleString
}