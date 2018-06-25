package net.corda.node.services.config

import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    @Test
    fun `validation has error when compatibilityZoneURL is present and devMode is true`() {
        val configuration = testConfiguration.copy(
                devMode = true,
                compatibilityZoneURL = URL("https://r3.com"))

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying { error -> error.contains("compatibilityZoneURL") && error.contains("devMode") }
    }

    @Test
    fun `validation has error when compatibilityZone is present and devMode is true`() {
        val configuration = testConfiguration.copy(devMode = true, networkServices = NetworkServicesConfig(
                URL("https://r3.com.doorman"),
                URL("https://r3.com/nm")))

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying { error -> error.contains("networkServices") && error.contains("devMode") }
    }

    @Test
    fun `validation has error when both compatibilityZoneURL and networkServices are configured`() {
        val configuration = testConfiguration.copy(
                devMode = false,
                compatibilityZoneURL = URL("https://r3.com"),
                networkServices = NetworkServicesConfig(
                        URL("https://r3.com.doorman"),
                        URL("https://r3.com/nm")))

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying {
            error -> error.contains("Cannot configure both compatibilityZoneUrl and networkServices simultaneously")
        }
    }

    @Test
    fun `compatiilityZoneURL populates NetworkServices`() {
        val compatibilityZoneURL = URI.create("https://r3.com").toURL()
        val configuration = testConfiguration.copy(
                devMode = false,
                compatibilityZoneURL = compatibilityZoneURL)

        assertNotNull(configuration.networkServices)
        assertEquals(compatibilityZoneURL, configuration.networkServices!!.doormanURL)
        assertEquals(compatibilityZoneURL, configuration.networkServices!!.networkMapURL)
    }

    private fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?): NodeConfiguration {
        return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
    }

    private fun testConfiguration(dataSourceProperties: Properties): NodeConfigurationImpl {
        return testConfiguration.copy(dataSourceProperties = dataSourceProperties)
    }

    private val testConfiguration = testNodeConfiguration()

    private fun testNodeConfiguration(): NodeConfigurationImpl {
        val baseDirectory = Paths.get(".")
        val keyStorePassword = "cordacadevpass"
        val trustStorePassword = "trustpass"
        val rpcSettings = NodeRpcSettings(
                address = NetworkHostAndPort("localhost", 1),
                adminAddress = NetworkHostAndPort("localhost", 2),
                standAloneBroker = false,
                useSsl = false,
                ssl = SslOptions(baseDirectory / "certificates", keyStorePassword, trustStorePassword))
        return NodeConfigurationImpl(
                baseDirectory = baseDirectory,
                myLegalName = ALICE_NAME,
                emailAddress = "",
                keyStorePassword = keyStorePassword,
                trustStorePassword = trustStorePassword,
                dataSourceProperties = makeTestDataSourceProperties(ALICE_NAME.organisation),
                rpcUsers = emptyList(),
                verifierType = VerifierType.InMemory,
                p2pAddress = NetworkHostAndPort("localhost", 0),
                messagingServerAddress = null,
                notary = null,
                certificateChainCheckPolicies = emptyList(),
                devMode = true,
                activeMQServer = ActiveMqServerConfiguration(BridgeConfiguration(0, 0, 0.0)),
                rpcSettings = rpcSettings
        )
    }
}
