package net.corda.ext.api.flow

import net.corda.core.CordaInternal
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.StateMachineTransactionMapping

@CordaInternal
interface StateMachineTransactionMappingFeed {
    fun track(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping>
}