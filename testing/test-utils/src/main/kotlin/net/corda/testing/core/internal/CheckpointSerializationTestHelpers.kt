package net.corda.testing.core.internal

import net.corda.testing.core.SerializationEnvironmentRule

/**
 * A test checkpoint serialization rule implementation for use in tests.
 *
 * @param inheritable whether new threads inherit the environment, use sparingly.
 */
class CheckpointSerializationEnvironmentRule(inheritable: Boolean = false) : SerializationEnvironmentRule(inheritable) {

    val checkpointSerializationFactory get() = env.checkpointSerializationFactory
    val checkpointSerializationContext get() = env.checkpointContext

}
