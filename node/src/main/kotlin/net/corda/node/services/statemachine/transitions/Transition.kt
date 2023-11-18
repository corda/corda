package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.StateMachineRunId
import net.corda.node.services.statemachine.StateMachineState
import java.security.SecureRandom

/**
 * An interface used to separate out different parts of the state machine transition function.
 */
interface Transition {
    /** The context of the transition. */
    val context: TransitionContext
    /** The state the transition is starting in. */
    val startingState: StateMachineState

    /** The (almost) pure transition function. The only side-effect we allow is random number generation. */
    fun transition(): TransitionResult

    /**
     * A helper
     */
    fun builder(build: TransitionBuilder.() -> FlowContinuation): TransitionResult {
        val builder = TransitionBuilder(context, startingState)
        val continuation = build(builder)
        return TransitionResult(builder.currentState, builder.actions, continuation)
    }

    /**
     * Add [element] to the [ArrayList] and return the list.
     *
     * Copy of [List.plus] that returns an [ArrayList] instead.
     */
    operator fun <T> ArrayList<T>.plus(element: T) : ArrayList<T> {
        val result = ArrayList<T>(size + 1)
        result.addAll(this)
        result.add(element)
        return result
    }

    /**
     * Add [elements] to the [ArrayList] and return the list.
     *
     * Copy of [List.plus] that returns an [ArrayList] instead.
     */
    operator fun <T> ArrayList<T>.plus(elements: Collection<T>) : ArrayList<T> {
        val result = ArrayList<T>(this.size + elements.size)
        result.addAll(this)
        result.addAll(elements)
        return result
    }

    /**
     * Convert the [List] into an [ArrayList].
     */
    fun <T> List<T>.toArrayList() : ArrayList<T> {
        return ArrayList(this)
    }
}

class TransitionContext(
        val id: StateMachineRunId,
        val secureRandom: SecureRandom
)
