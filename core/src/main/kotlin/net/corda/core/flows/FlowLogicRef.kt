package net.corda.core.flows

import net.corda.core.CordaInternal
import net.corda.core.DoNotImplement
import net.corda.core.serialization.CordaSerializable

/**
 * The public factory interface for creating validated [FlowLogicRef] instances as part of the scheduling framework.
 *
 * Typically this would be used from within the nextScheduledActivity method of a QueryableState to specify
 * the flow to run at the scheduled time.
 */
@DoNotImplement
interface FlowLogicRefFactory {
    /**
     * Construct a FlowLogicRef. This is intended for cases where the calling code has the relevant class already
     * and can provide it directly.
     */
    fun create(flowClass: Class<out FlowLogic<*>>, vararg args: Any?): FlowLogicRef

    /**
     * Construct a FlowLogicRef. This is intended for cases where the calling code does not want to require the flow
     * class on the classpath for all cases where the calling code is loaded.
     */
    fun create(flowClassName: String, vararg args: Any?): FlowLogicRef

    /**
     * @suppress
     * This is an internal method and should not be used: use [create] instead, which checks for the
     * [SchedulableFlow] annotation.
     */
    @CordaInternal
    fun createForRPC(flowClass: Class<out FlowLogic<*>>, vararg args: Any?): FlowLogicRef

    /**
     * Converts a [FlowLogicRef] object that was obtained from the calls above into a [FlowLogic], after doing some
     * validation to ensure it points to a legitimate flow class.
     */
    fun toFlowLogic(ref: FlowLogicRef): FlowLogic<*>
}

/**
 * Thrown if the structure of a class implementing a flow is not correct. There can be several causes for this such as
 * not inheriting from [FlowLogic], not having a valid constructor and so on.
 *
 * @property type the fully qualified name of the class that failed checks.
 */
@CordaSerializable
class IllegalFlowLogicException(val type: String, msg: String) :
        IllegalArgumentException("A FlowLogicRef cannot be constructed for FlowLogic of type $type: $msg") {
    constructor(type: Class<*>, msg: String) : this(type.name, msg)
}

/**
 * A handle interface representing a [FlowLogic] instance which would be possible to safely pass out of the contract sandbox.
 * Use FlowLogicRefFactory to construct a concrete security checked instance.
 *
 * Only allows a String reference to the FlowLogic class, and only allows restricted argument types as per [FlowLogicRefFactory].
 */
// TODO: align this with the existing [FlowRef] in the bank-side API (probably replace some of the API classes)
@CordaSerializable
@DoNotImplement
interface FlowLogicRef