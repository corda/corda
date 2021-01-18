package net.corda.node.services.statemachine

import net.corda.core.utilities.contextLogger

internal class StateMachineShutdownLogger(private val innerState: StateMachineInnerState) {

    private companion object {
        val log = contextLogger()
    }

    fun log() {
        innerState.withLock {
            val message = StringBuilder("Shutdown of the state machine is blocked.\n")
            val deadFlowMessage = StringBuilder()
            if (flows.isNotEmpty()) {
                message.append("The following live flows have not shutdown:\n")
                for ((id, flow) in flows) {
                    val state = flow.fiber.transientState
                    val line = "  - $id with properties " +
                            "[Status: ${state.checkpoint.status}, " +
                            "IO request: ${state.checkpoint.flowIoRequest ?: "Unstarted"}, " +
                            "Suspended: ${!state.isFlowResumed}, " +
                            "Last checkpoint timestamp: ${state.checkpoint.timestamp}, " +
                            "Killed: ${state.isKilled}]\n"
                    if (!state.isDead) {
                        message.append(line)
                    } else {
                        deadFlowMessage.append(line)
                    }
                }
            }
            if (pausedFlows.isNotEmpty()) {
                message.append("The following paused flows have not shutdown:\n")
                for ((id, flow) in pausedFlows) {
                    message.append(
                        "  - $id with properties " +
                                "[Status: ${flow.checkpoint.status}, " +
                                "IO request: ${flow.checkpoint.flowIoRequest ?: "Unstarted"}, " +
                                "Last checkpoint timestamp: ${flow.checkpoint.timestamp}, " +
                                "Resumable: ${flow.resumable}, " +
                                "Hospitalized: ${flow.hospitalized}]\n"
                    )
                }
            }
            if (deadFlowMessage.isNotEmpty()) {
                deadFlowMessage.insert(0, "The following dead (crashed) flows have not shutdown:\n")
                message.append(deadFlowMessage)
            }
            message.append("Manual intervention maybe be required for state machine shutdown due to these flows.\n")
            message.append("Continuing state machine shutdown loop...")
            log.info(message.toString())
        }
    }
}