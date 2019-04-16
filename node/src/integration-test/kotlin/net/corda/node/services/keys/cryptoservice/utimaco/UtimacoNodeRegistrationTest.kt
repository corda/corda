package net.corda.node.services.keys.cryptoservice.utimaco

import com.typesafe.config.ConfigFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.div
import net.corda.core.internal.toPath
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.hsm.HsmSimulator
import net.corda.node.services.keys.cryptoservice.ensurePrivateKeyIsNotInKeyStoreFile
import net.corda.node.utilities.registration.TestDoorman
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.SharedCompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URL
import java.nio.charset.Charset

@Ignore
class UtimacoNodeRegistrationTest : IntegrationTest() {

    @Rule
    @JvmField
    val configFolder = TemporaryFolder()

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val portAllocation = incrementalPortAllocation(13400)

    @Rule
    @JvmField
    val doorman = TestDoorman(portAllocation)

    @Rule
    @JvmField
    val hsmSimulator = HsmSimulator(portAllocation)

    companion object {
        private val notaryName = CordaX500Name("NotaryService", "Zurich", "CH")
        private val aliceName = CordaX500Name("Alice", "London", "GB")
        private val genevieveName = CordaX500Name("Genevieve", "London", "GB")

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(notaryName, aliceName, genevieveName)
    }

    @Test
    fun `node registration with one node backed by Utimaco HSM`() {
        val tmpUtimacoConfig = createTempUtimacoConfig()

        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://${doorman.serverHostAndPort}"),
                null,
                publishNotaries = { doorman.server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = listOf(NotarySpec(notaryName)),
                cordappsForAllNodes = FINANCE_CORDAPPS,
                notaryCustomOverrides = mapOf("devMode" to false, "cordappSignerKeyFingerprintBlacklist" to listOf<String>())
        ) {
            val (alice, genevieve) = listOf(
                    startNode(providedName = aliceName, customOverrides = mapOf(
                            "devMode" to false,
                            "cordappSignerKeyFingerprintBlacklist" to listOf<String>(),
                            "cryptoServiceName" to "UTIMACO",
                            "cryptoServiceConf" to tmpUtimacoConfig
                    )),
                    startNode(providedName = genevieveName, customOverrides = mapOf(
                            "devMode" to false,
                            "cordappSignerKeyFingerprintBlacklist" to listOf<String>()
                    ))
            ).transpose().getOrThrow()

            ensurePrivateKeyIsNotInKeyStoreFile(CORDA_CLIENT_CA, alice.baseDirectory / "certificates" / "nodekeystore.jks")
            ensurePrivateKeyIsNotInKeyStoreFile("${X509Utilities.NODE_IDENTITY_ALIAS_PREFIX}-private-key", alice.baseDirectory / "certificates" / "nodekeystore.jks")

            val anonymous = false
            val result = alice.rpc.startFlow(
                    ::CashIssueAndPaymentFlow,
                    1000.DOLLARS,
                    OpaqueBytes.of(12),
                    genevieve.nodeInfo.singleIdentity(),
                    anonymous,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()

            // make sure the transaction was actually signed by the key in the hsm
            val utimacoCryptoService = UtimacoCryptoService.fromConfigurationFile(File(tmpUtimacoConfig).toPath())
            val alicePubKey = utimacoCryptoService.getPublicKey("${X509Utilities.NODE_IDENTITY_ALIAS_PREFIX}-private-key")
            assertThat(alicePubKey).isNotNull()
            assertThat(result.stx.sigs.map { it.by.encoded!! }.filter { it.contentEquals(alicePubKey!!.encoded) }).hasSize(1)
            assertThat(result.stx.sigs.single { it.by.encoded!!.contentEquals(alicePubKey!!.encoded) }.isValid(result.stx.id))
        }
    }

    private fun createTempUtimacoConfig(): String {
        val utimacoConfig = ConfigFactory.parseFile(javaClass.getResource("utimaco.conf").toPath().toFile())
        val portConfig = ConfigFactory.parseMap(mapOf("port" to hsmSimulator.address.port))
        val config = portConfig.withFallback(utimacoConfig)
        val tmpConfigFile = configFolder.newFile("utimaco.conf")
        FileUtils.writeStringToFile(tmpConfigFile, config.root().render(), Charset.defaultCharset())
        return tmpConfigFile.absolutePath
    }
}
