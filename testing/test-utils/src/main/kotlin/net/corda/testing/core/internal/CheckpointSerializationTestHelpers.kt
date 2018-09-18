package net.corda.testing.core.internal

import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A test checkpoint serialization rule implementation for use in tests.
 *
 * @param inheritable whether new threads inherit the environment, use sparingly.
 */
class CheckpointSerializationEnvironmentRule(inheritable: Boolean = false) : TestRule {

    val innerRule = SerializationEnvironmentRule(inheritable)

    override fun apply(base: Statement, description: Description): Statement =
            innerRule.apply(base, description)

    val checkpointSerializationFactory get() = innerRule.env.checkpointSerializationFactory
    val checkpointSerializationContext get() = innerRule.env.checkpointContext
}
