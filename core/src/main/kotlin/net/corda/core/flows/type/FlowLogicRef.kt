package net.corda.core.flows.type

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable

/**
 * The public factory interface for creating validated FlowLogicRef instances as part of the scheduling framework.
 * Typically this would be used from within the nextScheduledActivity method of a QueryableState to specify
 * the flow to run at the scheduled time.
 */
interface FlowLogicRefFactory {
    fun create(flowClass: Class<out FlowLogic<*>>, vararg args: Any?): FlowLogicRef
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
interface FlowLogicRef


/**
 * This is just some way to track what attachments need to be in the class loader, but may later include some app
 * properties loaded from the attachments.  And perhaps the authenticated user for an API call?
 */
@CordaSerializable
data class AppContext(val attachments: List<SecureHash>) {
    // TODO: build a real [AttachmentsClassLoader] etc
    val classLoader: ClassLoader
        get() = this.javaClass.classLoader
}
