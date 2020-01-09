package net.corda.ext.api.flow

import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.Try

/**
 * Container for informing interested parties that `StateMachineManager` is dealing with an instance of [FlowLogic].
 */
sealed class Change {

    abstract val logic: FlowLogic<*>

    data class Add(override val logic: FlowLogic<*>) : Change()
    data class Removed(override val logic: FlowLogic<*>, val result: Try<*>) : Change()
}