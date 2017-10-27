package net.corda.testing

import org.junit.Rule

@Deprecated("Instead of extending this class, use SerializationEnvironmentRule in the same way.")
abstract class TestDependencyInjectionBase {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
}