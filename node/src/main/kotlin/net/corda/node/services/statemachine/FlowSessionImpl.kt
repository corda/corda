package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.utilities.UntrustworthyData

class FlowSessionImpl(override val counterparty: Party) : FlowSession() {
    internal lateinit var stateMachine: FlowStateMachine<*>
    internal lateinit var sessionFlow: FlowLogic<*>

    @Suspendable
    override fun getCounterpartyFlowInfo(maySkipCheckpoint: Boolean): FlowInfo {
        return stateMachine.getFlowInfo(counterparty, sessionFlow, maySkipCheckpoint)
    }

    @Suspendable
    override fun getCounterpartyFlowInfo() = getCounterpartyFlowInfo(maySkipCheckpoint = false)

    @Suspendable
    override fun <R : Any> sendAndReceive(
            receiveType: Class<R>,
            payload: Any,
            maySkipCheckpoint: Boolean
    ): UntrustworthyData<R> {
        return stateMachine.sendAndReceive(
                receiveType,
                counterparty,
                payload,
                sessionFlow,
                retrySend = false,
                maySkipCheckpoint = maySkipCheckpoint
        )
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any) = sendAndReceive(receiveType, payload, maySkipCheckpoint = false)

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>, maySkipCheckpoint: Boolean): UntrustworthyData<R> {
        return stateMachine.receive(receiveType, counterparty, sessionFlow, maySkipCheckpoint)
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>) = receive(receiveType, maySkipCheckpoint = false)

    @Suspendable
    override fun send(payload: Any, maySkipCheckpoint: Boolean) {
        return stateMachine.send(counterparty, payload, sessionFlow, maySkipCheckpoint)
    }

    @Suspendable
    override fun send(payload: Any) = send(payload, maySkipCheckpoint = false)

    override fun toString() = "Flow session with $counterparty"
}

