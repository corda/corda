package net.corda.node.internal.serialization.testutils

import com.google.common.collect.SetMultimap
import net.corda.core.context.Trace
import net.corda.node.services.messaging.ObservableContextInterface
import net.corda.node.services.messaging.ObservableSubscriptionMap
import net.corda.nodeapi.RPCApi
import org.apache.activemq.artemis.api.core.SimpleString

class TestObservableContext(
        override val observableMap: ObservableSubscriptionMap,
        override val clientAddressToObservables: SetMultimap<SimpleString, Trace.InvocationId>,
        override val deduplicationIdentity: String,
        override val clientAddress: SimpleString
) : ObservableContextInterface {
    override fun sendMessage(serverToClient: RPCApi.ServerToClient) {
        println ("\n\nHALP\n\n")
    }

}