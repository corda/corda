package net.corda.node.migration

import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.cordappsForPackages
import org.apache.commons.lang3.SystemUtils
import org.hibernate.internal.SessionFactoryRegistry
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertFalse

class MemoryLeakTest {
    @Test(timeout=300_000)
    fun `memory leak test`() {
        assertFalse(SessionFactoryRegistry.INSTANCE.hasRegistrations())
        repeat(1) {
            // Start mock network
            Assume.assumeTrue(!SystemUtils.IS_JAVA_11)
            val mockNetwork = MockNetwork(
                    MockNetworkParameters(
                            cordappsForAllNodes = cordappsForPackages(
                                    listOf(
                                            "net.corda.node.services.vault"
                                    )
                            )
                    )
            ).also { mockNetwork ->
                mockNetwork.createPartyNode(ALICE_NAME)
            }

            // Stop mock network
            mockNetwork?.stopNodes()
            // mockNetwork = null
        }
        assertFalse(SessionFactoryRegistry.INSTANCE.hasRegistrations())
    }
}

