package net.corda.testing.node.internal

import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.testing.driver.TestCorDapp
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class InternalMockNetworkTests {
    @Test
    fun `does not leak serialization env if init fails`() {
        val e = Exception("didn't work")
        assertThatThrownBy {
            object : InternalMockNetwork(cordappsForAllNodes = emptySet()) {
                override fun createNotaries() = throw e
            }
        }.isSameAs(e)
        assertThatThrownBy { effectiveSerializationEnv }.isInstanceOf(IllegalStateException::class.java)
    }
}