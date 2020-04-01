package net.corda.node

import net.corda.contracts.serialization.whitelist.WhitelistData
import net.corda.core.contracts.TransactionVerificationException.ContractRejection
import net.corda.flows.serialization.whitelist.WhitelistFlow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith

@Suppress("FunctionName")
class MockNetworkSerializationWhitelistTest {
    companion object {
        private const val DATA = 654321L

        @JvmField
        val contractCordapp = cordappWithPackages("net.corda.contracts.serialization.whitelist").signed()

        @JvmField
        val workflowCordapp = cordappWithPackages("net.corda.flows.serialization.whitelist").signed()

        @JvmField
        val badData = WhitelistData(DATA)

        @BeforeClass
        @JvmStatic
        fun checkData() {
            assertNotCordaSerializable<WhitelistData>()
        }
    }

    private lateinit var mockNetwork: MockNetwork

    @Before
    fun setup() {
        mockNetwork = MockNetwork(
            MockNetworkParameters(
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                cordappsForAllNodes = listOf(contractCordapp, workflowCordapp)
            )
        )
    }

    @After
    fun shutdown() {
        mockNetwork.stopNodes()
    }

    @Test(timeout = 300_000)
    fun `test serialization whitelist with mock network`() {
        val node = mockNetwork.createPartyNode()
        val ex = assertFailsWith<ExecutionException> {
            node.startFlow(WhitelistFlow(badData)).get()
        }
        assertThat(ex)
            .hasCauseExactlyInstanceOf(ContractRejection::class.java)
            .hasMessageContaining("WhitelistData $badData exceeds maximum value!")
    }
}