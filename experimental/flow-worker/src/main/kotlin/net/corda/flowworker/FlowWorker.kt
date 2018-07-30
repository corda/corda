package net.corda.flowworker

import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.FlowStateMachine
import net.corda.node.services.statemachine.ExternalEvent

class FlowWorker(private val flowWorkerServiceHub: FlowWorkerServiceHub) {

    fun start() {
        flowWorkerServiceHub.start()
    }

    fun stop() {
        flowWorkerServiceHub.stop()
    }

    fun <T> startFlow(event: ExternalEvent.ExternalStartFlowEvent<T>): CordaFuture<FlowStateMachine<T>> {
        flowWorkerServiceHub.database.transaction {
            flowWorkerServiceHub.smm.deliverExternalEvent(event)
        }
        return event.future
    }

}