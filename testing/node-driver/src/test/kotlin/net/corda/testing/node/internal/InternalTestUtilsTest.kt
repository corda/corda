package net.corda.testing.node.internal

import net.corda.core.serialization.internal._driverSerializationEnv
import net.corda.coretesting.internal.asTestContextEnv
import net.corda.coretesting.internal.createTestSerializationEnv
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class InternalTestUtilsTest {
    @Test(timeout=300_000)
	fun `test simplifyScanPackages`() {
        assertThat(simplifyScanPackages(emptyList())).isEmpty()
        assertThat(simplifyScanPackages(listOf("com.foo.bar"))).containsExactlyInAnyOrder("com.foo.bar")
        assertThat(simplifyScanPackages(listOf("com.foo", "com.foo"))).containsExactlyInAnyOrder("com.foo")
        assertThat(simplifyScanPackages(listOf("com.foo", "com.bar"))).containsExactlyInAnyOrder("com.foo", "com.bar")
        assertThat(simplifyScanPackages(listOf("com.foo", "com.foo.bar"))).containsExactlyInAnyOrder("com.foo")
        assertThat(simplifyScanPackages(listOf("com.foo.bar", "com.foo"))).containsExactlyInAnyOrder("com.foo")
        assertThat(simplifyScanPackages(listOf("com.foobar", "com.foo.bar"))).containsExactlyInAnyOrder("com.foobar", "com.foo.bar")
        assertThat(simplifyScanPackages(listOf("com.foobar", "com.foo"))).containsExactlyInAnyOrder("com.foobar", "com.foo")
    }

    /**
     * A driver test that hits its JUnit timeout leaves its driver shutting down on the abandoned test thread. Meanwhile the
     * next test's SerializationEnvironmentRule replaces the driver's environment (logging a warning). When the driver
     * finally closed, it used to clear the rule's environment, and the rule then failed with
     * "driverSerializationEnv is already null".
     */
    @Test(timeout=300_000)
    fun `closing a driver serialization environment that has been replaced leaves the replacement in place`() {
        val driverEnv = setDriverSerialization()!!
        try {
            createTestSerializationEnv().asTestContextEnv { ruleEnv ->
                driverEnv.close()
                assertThat(_driverSerializationEnv.get()).isSameAs(ruleEnv)
            }
            assertThat(_driverSerializationEnv.get()).isNull()
        } finally {
            if (_driverSerializationEnv.get() != null) {
                _driverSerializationEnv.set(null)
            }
        }
    }
}
