package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.utilities.UntrustworthyData

// TODO docs
abstract class FlowSession {
    abstract val counterparty: Party

    @Suspendable
    abstract fun getCounterpartyFlowInfo(): FlowInfo

    @Suspendable
    inline fun <reified R : Any> sendAndReceive(payload: Any): UntrustworthyData<R> {
        return sendAndReceive(R::class.java, payload)
    }
    @Suspendable
    abstract fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R>

    @Suspendable
    inline fun <reified R : Any> receive(): UntrustworthyData<R> {
        return receive(R::class.java)
    }
    @Suspendable
    abstract fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R>

    @Suspendable
    abstract fun send(payload: Any)
}
