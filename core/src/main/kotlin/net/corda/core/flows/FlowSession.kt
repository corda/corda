package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.utilities.UntrustworthyData

// TODO docs
class FlowSession(
        val counterparty: Party,
        private val stateMachine: FlowStateMachine<*>,
        private val sessionFlow: FlowLogic<*>
) {
    @Suspendable
    fun getCounterpartyFlowInfo(): FlowInfo {
        return stateMachine.getFlowInfo(counterparty, sessionFlow)
    }

    @Suspendable
    inline fun <reified R : Any> sendAndReceive(payload: Any): UntrustworthyData<R> {
        return sendAndReceive(R::class.java, payload)
    }
    @Suspendable
    fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
        return stateMachine.sendAndReceive(receiveType, counterparty, payload, sessionFlow)
    }

    @Suspendable
    internal inline fun <reified R : Any> FlowSession.sendAndReceiveWithRetry(payload: Any): UntrustworthyData<R> {
        return sendAndReceiveWithRetry(R::class.java, payload)
    }
    @Suspendable
    internal fun <R : Any> sendAndReceiveWithRetry(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
        return stateMachine.sendAndReceive(receiveType, counterparty, payload, sessionFlow, retrySend = true)
    }

    @Suspendable
    inline fun <reified R : Any> receive(): UntrustworthyData<R> {
        return receive(R::class.java)
    }
    @Suspendable
    fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R> {
        return stateMachine.receive(receiveType, counterparty, sessionFlow)
    }

    @Suspendable
    fun send(payload: Any) {
        return stateMachine.send(counterparty, payload, sessionFlow)
    }
}

