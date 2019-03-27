package net.corda.node.utilities

import net.corda.core.flows.FlowLogic

/** Get the ID of the current flow. */
internal val currentFlowId: String?
    get() = FlowLogic.currentTopLevel?.stateMachine?.id?.uuid?.toString()
