package net.corda.node.services.keys.cryptoservice.gemalto

import com.safenetinc.luna.provider.LunaProvider
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
import net.corda.node.services.keys.cryptoservice.ensurePrivateKeyIsNotInKeyStoreFile
import net.corda.node.utilities.registration.TestDoorman
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.gemalto.GemaltoLunaCryptoService
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.net.URL
import java.nio.file.Path
import java.security.KeyStore

/*
 * See the comment on [GemaltoLunaCryptoServiceTest] as to why this test is not enabled.
 */
@Ignore
class GemaltoLunaNodeRegistrationTest : IntegrationTest() {

    private val configPath = javaClass.getResource("gemalto.conf").toPath()
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val portAllocation = incrementalPortAllocation()

    @Rule
    @JvmField
    val doorman = TestDoorman(portAllocation)

    companion object {
        private val notaryName = CordaX500Name("NotaryService", "Zurich", "CH")
        private val aliceName = CordaX500Name("Alice", "London", "GB")
        private val genevieveName = CordaX500Name("Genevieve", "London", "GB")

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(notaryName, aliceName, genevieveName)
    }

    @Test
    fun `node registration with one node backed by Gemalto Luna HSM`() {
        deleteExistingLegalIdentity()

        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://${doorman.serverHostAndPort}"),
                null,
                publishNotaries = { doorman.server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)
        internalDriver(
                // only needed if the JCA provider is not installed system-wide.
//                systemProperties = mapOf("java.library.path" to "/home/florian/corda/enterprise/node/src/test/resources/net/corda/node/services/keys/cryptoservice/gemalto/lunaclient/jsp/lib"),
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
                            "cryptoServiceName" to "GEMALTO_LUNA",
                            "cryptoServiceConf" to configPath.toFile().absolutePath
                    )),
                    startNode(providedName = genevieveName, customOverrides = mapOf(
                            "devMode" to false,
                            "cordappSignerKeyFingerprintBlacklist" to listOf<String>()
                    ))
            ).transpose().getOrThrow()

            val anonymous = false
            val result = alice.rpc.startFlow(
                    ::CashIssueAndPaymentFlow,
                    1000.DOLLARS,
                    OpaqueBytes.of(12),
                    genevieve.nodeInfo.singleIdentity(),
                    anonymous,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()

            ensurePrivateKeyIsNotInKeyStoreFile(X509Utilities.CORDA_CLIENT_CA, alice.baseDirectory / "certificates" / "nodekeystore.jks")
            ensurePrivateKeyIsNotInKeyStoreFile("${X509Utilities.NODE_IDENTITY_ALIAS_PREFIX}-private-key", alice.baseDirectory / "certificates" / "nodekeystore.jks")

            // make sure the transaction was actually signed by the key in the hsm
            val gemaltoLunaCryptoService = GemaltoLunaCryptoService.fromConfigurationFile(aliceName.x500Principal, configPath)
            val alicePubKey = gemaltoLunaCryptoService.getPublicKey("identity-private-key")
            assertThat(alicePubKey).isNotNull()
            assertThat(result.stx.sigs.map { it.by.encoded!! }.filter { it.contentEquals(alicePubKey!!.encoded) }).hasSize(1)
            assertThat(result.stx.sigs.single { it.by.encoded!!.contentEquals(alicePubKey!!.encoded) }.isValid(result.stx.id))
        }
    }

    private fun deleteExistingLegalIdentity() {
        val config = ConfigFactory.parseFile(configPath.toFile()).resolve().parseAs(GemaltoConfig::class)
        val provider = LunaProvider.getInstance()
        val keyStore = KeyStore.getInstance(GemaltoLunaCryptoService.KEYSTORE_TYPE, provider)
        keyStore.load(config.keyStore.byteInputStream(), config.password.toCharArray())
        keyStore.deleteEntry("identity-private-key")
    }
    data class GemaltoConfig(val keyStore: String, val password: String)
}
