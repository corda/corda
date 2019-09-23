package net.corda.node.services.statemachine

import net.corda.core.CordaException
import net.corda.core.serialization.ConstructorForDeserialization

// TODO This exception should not be propagated up to rpc as it suppresses the real exception
class StateTransitionException(
    val transitionAction: Action?,
    val transitionEvent: Event?,
    val exception: Exception
) : CordaException(exception.message, exception) {

    @ConstructorForDeserialization
    constructor(exception: Exception): this(null, null, exception)
}

internal class AsyncOperationTransitionException(exception: Exception) : CordaException(exception.message, exception)
