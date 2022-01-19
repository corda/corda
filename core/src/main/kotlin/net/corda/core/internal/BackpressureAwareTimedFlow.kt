package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaInternal
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.WaitTimeUpdate
import net.corda.core.utilities.UntrustworthyData

/**
 * Implementation of TimedFlow that can handle WaitTimeUpdate messages. Any flow talking to the notary should implement this and use
 * explicit send and this class's receiveResultOrTiming to receive the response to handle cases where the notary sends a timeout update.
 *
 * This is handling the special case of the notary where the notary service will have an internal queue on the uniqueness provider and we
 * want to stop retries overwhelming that internal queue. As the TimedFlow mechanism and the notary service back-pressure are very specific
 * to this use case at the moment, this implementation is internal and not for general use.
 */
abstract class BackpressureAwareTimedFlow<ResultType> : FlowLogic<ResultType>(), TimedFlow {
    @CordaInternal
    @Suspendable
    inline fun <reified ReceiveType> receiveResultOrTiming(session: FlowSession): UntrustworthyData<ReceiveType> {
        while (true) {
            val wrappedResult = session.receive<Any>()
            val unwrapped = wrappedResult.fromUntrustedWorld
            when (unwrapped) {
                is WaitTimeUpdate -> {
                    applyWaitTimeUpdate(session, unwrapped)
                }
                is ReceiveType -> @Suppress("UNCHECKED_CAST") // The compiler doesn't understand it's checked in the line above
                return wrappedResult as UntrustworthyData<ReceiveType>
                else -> throw throw IllegalArgumentException("We were expecting a ${ReceiveType::class.java.name} or WaitTimeUpdate but " +
                        "we instead got a ${unwrapped.javaClass.name} ($unwrapped)")
            }
        }
    }

    open fun applyWaitTimeUpdate(session: FlowSession, update: WaitTimeUpdate) {
        logger.info("Counterparty [${session.counterparty}] is busy - TimedFlow $runId has been asked to wait for an additional " +
                "${update.waitTime} for completion.")
        stateMachine.updateTimedFlowTimeout(update.waitTime.seconds)
    }
}
