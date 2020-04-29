package net.corda.node.services.statemachine

import net.corda.core.flows.StateMachineRunId

data class StateMachineInstanceId(val runId: StateMachineRunId, val fiberId: Long)