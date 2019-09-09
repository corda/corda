package net.corda.node.services.keys.cryptoservice

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
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
import java.lang.IllegalStateException
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
    fun `when initial registration is done without secure confidential identities enabled, then subsequent startup with this enabled will fail`() {
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

            val node = startNode(this, secureConfidentialIdentitiesEnabled = false, devMode = false)
            node.stop()

            assertThatThrownBy {
                startNode(this, secureConfidentialIdentitiesEnabled = true, devMode = false)
            }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("The crypto service configured for fresh identities (BC_SIMPLE) does not contain a key under the alias: wrapping-key-alias.")

        }
    }

    @Test
    fun `when initial registration is done without secure confidential identities enabled but with devMode=true, then subsequent startup will create the key if missing`() {
        internalDriver(startNodesInProcess = false,
                portAllocation = portAllocation) {

            val node = startNode(this, secureConfidentialIdentitiesEnabled = false, devMode = true)
            node.stop()

            startNode(this, secureConfidentialIdentitiesEnabled = true, devMode = true)
        }
    }

    @Test
    fun `initial registration creates the wrapping key successfully and node can start up multiple times afterwards`() {
        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://${doorman.serverHostAndPort}"),
                null,
                publishNotaries = { doorman.server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)

        internalDriver(startNodesInProcess = false,
                portAllocation = portAllocation,
                notarySpecs = listOf(NotarySpec(notaryName)),
                compatibilityZone = compatibilityZone,
                notaryCustomOverrides = mapOf("devMode" to false)) {

            val node = startNode(this, secureConfidentialIdentitiesEnabled = true, devMode = false)
            node.stop()
            startNode(this, secureConfidentialIdentitiesEnabled = true, devMode = false)
        }
    }

    private fun startNode(driver: DriverDSL, secureConfidentialIdentitiesEnabled: Boolean, devMode: Boolean): NodeHandle {
        val config = if (secureConfidentialIdentitiesEnabled)
            mapOf("devMode" to devMode,
                  "cordappSignerKeyFingerprintBlacklist" to listOf<String>(),
                  "freshIdentitiesConfiguration" to mapOf(
                        "mode" to "DEGRADED_WRAPPED",
                        "cryptoServiceConfiguration" to mapOf(
                                "cryptoServiceName" to "BC_SIMPLE"
                        )
                  )
            )
        else
            mapOf("devMode" to devMode,
                  "cordappSignerKeyFingerprintBlacklist" to listOf<String>()
            )


        return driver.startNode(providedName = aliceName, customOverrides = config).getOrThrow()
    }

}