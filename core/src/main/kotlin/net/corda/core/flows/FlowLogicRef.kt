package net.corda.core.flows

import net.corda.core.DoNotImplement
import net.corda.core.serialization.CordaSerializable

/**
 * The public factory interface for creating validated FlowLogicRef instances as part of the scheduling framework.
 * Typically this would be used from within the nextScheduledActivity method of a QueryableState to specify
 * the flow to run at the scheduled time.
 */
@DoNotImplement
interface FlowLogicRefFactory {
    /**
     * Construct a FlowLogicRef. This is intended for cases where the calling code has the relevant class already
     * and can provide it directly.
     */
    @Deprecated("This should be avoided, and the version which takes a class name used instead to avoid requiring the class on the classpath to deserialize calling code")
    fun create(flowClass: Class<out FlowLogic<*>>, vararg args: Any?): FlowLogicRef
    /**
     * Construct a FlowLogicRef. This is intended for cases where the calling code does not want to require the flow
     * class on the classpath for all cases where the calling code is loaded.
     */
    fun create(flowClassName: String, vararg args: Any?): FlowLogicRef
    fun createForRPC(flowClass: Class<out FlowLogic<*>>, vararg args: Any?): FlowLogicRef
    fun toFlowLogic(ref: FlowLogicRef): FlowLogic<*>
}

@CordaSerializable
class IllegalFlowLogicException(type: Class<*>, msg: String) : IllegalArgumentException(
        "${FlowLogicRef::class.java.simpleName} cannot be constructed for ${FlowLogic::class.java.simpleName} of type ${type.name} $msg")

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