package net.corda.node.services.keys.cryptoservice

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.ConfigurationException
import net.corda.node.utilities.registration.TestDoorman
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.IntegrationTest.Companion.isRemoteDatabaseMode
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.SharedCompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URL

class NodeStartupForWrappedKeysTest {

    companion object {
        internal val notaryName = CordaX500Name("NotaryService", "Zurich", "CH")
        internal val aliceName = CordaX500Name("Alice", "London", "GB")
    }

    private val portAllocation = incrementalPortAllocation()

    @Rule
    @JvmField
    val doorman = TestDoorman(portAllocation)

    @Before
    fun setup() {
        // no need to run these tests with remote DBs
        Assume.assumeFalse(isRemoteDatabaseMode())
    }

    @Test
    fun `when configuration instructs to avoid creating the key on startup, but the key does not exist during startup, the node exits`() {
        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://${doorman.serverHostAndPort}"),
                null,
                publishNotaries = { doorman.server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)

        internalDriver(startNodesInProcess = true,
                portAllocation = portAllocation,
                notarySpecs = listOf(NotarySpec(notaryName)),
                compatibilityZone = compatibilityZone,
                notaryCustomOverrides = mapOf("devMode" to false)) {
            assertThatThrownBy {
                startNode(this, "NO")
            }
            .isInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("The crypto service configured for fresh identities (BC_SIMPLE) does not contain a key under the alias: wrapping-key-alias. However, createDuringStartup is set to NO")
        }
    }

    @Test
    fun `when configuration instructs to create the key on startup, but the key already exists, the node exits`() {
        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://${doorman.serverHostAndPort}"),
                null,
                publishNotaries = { doorman.server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)

        internalDriver(startNodesInProcess = true,
                portAllocation = portAllocation,
                notarySpecs = listOf(NotarySpec(notaryName)),
                compatibilityZone = compatibilityZone,
                notaryCustomOverrides = mapOf("devMode" to false)) {

            val node = startNode(this, "YES")
            node.stop()

            assertThatThrownBy {
                startNode(this, "YES")
            }
            .isInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("The crypto service configured for fresh identities (BC_SIMPLE) already contains a key under the alias: wrapping-key-alias. However, createDuringStartup is set to YES")
        }
    }

    @Test
    fun `node can be re-started multiple times, when createDuringStartup is set to ONLY_IF_MISSING`() {
        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://${doorman.serverHostAndPort}"),
                null,
                publishNotaries = { doorman.server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)

        internalDriver(startNodesInProcess = true,
                portAllocation = portAllocation,
                notarySpecs = listOf(NotarySpec(notaryName)),
                compatibilityZone = compatibilityZone,
                notaryCustomOverrides = mapOf("devMode" to false)) {

            val node = startNode(this, "ONLY_IF_MISSING")
            node.stop()
            startNode(this, "ONLY_IF_MISSING")
        }
    }

    @Test
    fun `node can be re-started multiple times, when createDuringStartup is switched to NO after the first startup`() {
        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://${doorman.serverHostAndPort}"),
                null,
                publishNotaries = { doorman.server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)

        internalDriver(startNodesInProcess = true,
                portAllocation = portAllocation,
                notarySpecs = listOf(NotarySpec(notaryName)),
                compatibilityZone = compatibilityZone,
                notaryCustomOverrides = mapOf("devMode" to false)) {

            val node = startNode(this, "YES")
            node.stop()
            startNode(this, "NO")
        }
    }

    private fun startNode(driver: DriverDSL, createDuringStartup: String): NodeHandle {
        return driver.startNode(providedName = aliceName, customOverrides = mapOf(
                "devMode" to false,
                "cordappSignerKeyFingerprintBlacklist" to listOf<String>(),
                "freshIdentitiesConfiguration" to mapOf(
                        "mode" to "DEGRADED_WRAPPED",
                        "cryptoServiceConfiguration" to mapOf(
                                "cryptoServiceName" to "BC_SIMPLE"
                        ),
                        "createDuringStartup" to createDuringStartup
                )
        )).getOrThrow()
    }

}