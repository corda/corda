package net.corda.node.services.config

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeConfigurationImplTest {
    @Test
    fun `can't have dev mode options if not in dev mode`() {
        val debugOptions = DevModeOptions()
        configDebugOptions(true, debugOptions)
        configDebugOptions(true, null)
        assertThatThrownBy { configDebugOptions(false, debugOptions) }.hasMessageMatching("Cannot use devModeOptions outside of dev mode")
        configDebugOptions(false, null)
    }

    @Test
    fun `check devModeOptions flag helper`() {
        assertTrue { configDebugOptions(true, null).shouldCheckCheckpoints() }
        assertTrue { configDebugOptions(true, DevModeOptions()).shouldCheckCheckpoints() }
        assertTrue { configDebugOptions(true, DevModeOptions(false)).shouldCheckCheckpoints() }
        assertFalse { configDebugOptions(true, DevModeOptions(true)).shouldCheckCheckpoints() }
    }

    private fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?) : NodeConfiguration {
        return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
    }

    private val testConfiguration = NodeConfigurationImpl(
            baseDirectory = Paths.get("."),
            myLegalName = ALICE_NAME,
            emailAddress = "",
            keyStorePassword = "cordacadevpass",
            trustStorePassword = "trustpass",
            dataSourceProperties = makeTestDataSourceProperties(ALICE_NAME.organisation),
            rpcUsers = emptyList(),
            verifierType = VerifierType.InMemory,
            useHTTPS = false,
            p2pAddress = NetworkHostAndPort("localhost", 0),
            rpcAddress = NetworkHostAndPort("localhost", 1),
            messagingServerAddress = null,
            notary = null,
            certificateChainCheckPolicies = emptyList(),
            devMode = true,
            activeMQServer = ActiveMqServerConfiguration(BridgeConfiguration(0, 0, 0.0)))
}
