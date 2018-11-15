package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.WaitTimeUpdate
import net.corda.core.utilities.UntrustworthyData

abstract class BackpressureAwareTimedFlow<ResultType, ReceiveType>(val receiveType: Class<ReceiveType>) : FlowLogic<ResultType>(), TimedFlow {
    @Suspendable
    fun <T> receiveResultOrTiming(session: FlowSession): UntrustworthyData<ReceiveType> {
        while (true) {
            val wrappedResult = session.receive<Any>()
            val unwrapped = wrappedResult.fromUntrustedWorld
            if (unwrapped is WaitTimeUpdate) {
                logger.info("Counterparty is busy - TimedFlow $runId has been asked to wait for an additional ${unwrapped.waitTimeSeconds} seconds for completion.")
                stateMachine.updateTimedFlowTimeout(unwrapped.waitTimeSeconds)
            } else if (unwrapped::class.java == receiveType) {
                @Suppress("UNCHECKED_CAST") // The compiler doesn't understand it's checked in the line above
                return wrappedResult as UntrustworthyData<ReceiveType>
            } else {
                throw throw IllegalArgumentException("We were expecting a ${receiveType.name} or WaitTimeUpdate but we instead got a ${unwrapped.javaClass.name} ($unwrapped)")
            }
        }
    }
}