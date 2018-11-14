package net.corda.core.internal

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.WaitTimeUpdate
import net.corda.core.utilities.UntrustworthyData

abstract class BackpressureAwareTimedFlow<ResultType, ReceiveType>(val receiveType: Class<ReceiveType>) : FlowLogic<ResultType>(), TimedFlow {
    fun <T> receiveResultOrTiming(session: FlowSession): UntrustworthyData<ReceiveType> {
        while (true) {
            val wrappedResult = session.receive<Any>()
            val unwrapped = wrappedResult.fromUntrustedWorld
            if (unwrapped is WaitTimeUpdate) {

            } else if (unwrapped::class.java == receiveType) {
                return wrappedResult as UntrustworthyData<ReceiveType>
            } else {
                throw throw IllegalArgumentException("We were expecting a ${receiveType.name} or WaitTimeUpdate but we instead got a ${unwrapped.javaClass.name} ($unwrapped)")
            }
        }
    }
}