package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.utilities.UntrustworthyData

class FlowSessionImpl(
        override val counterparty: Party
) : FlowSession() {

    internal lateinit var stateMachine: FlowStateMachine<*>
    internal lateinit var sessionFlow: FlowLogic<*>

    @Suspendable
    override fun getCounterpartyFlowInfo(): FlowInfo {
        return stateMachine.getFlowInfo(counterparty, sessionFlow)
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
        return stateMachine.sendAndReceive(receiveType, counterparty, payload, sessionFlow)
    }

    @Suspendable
    internal fun <R : Any> sendAndReceiveWithRetry(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
        return stateMachine.sendAndReceive(receiveType, counterparty, payload, sessionFlow, retrySend = true)
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R> {
        return stateMachine.receive(receiveType, counterparty, sessionFlow)
    }

    @Suspendable
    override fun send(payload: Any) {
        return stateMachine.send(counterparty, payload, sessionFlow)
    }
}

