package net.corda.node.services.keys.cryptoservice

import net.corda.core.crypto.internal.AliasPrivateKey
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.declaredField
import net.corda.core.internal.div
import net.corda.core.internal.inputStream
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.utilities.registration.TestDoorman
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.crypto.KEYSTORE_TYPE
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoService
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
import org.junit.After
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import sun.security.pkcs.PKCS8Key
import sun.security.util.DerValue
import sun.security.x509.AlgorithmId
import java.net.URL
import java.nio.file.Path
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class AbstractNodeRegistrationTest : IntegrationTest() {

    internal open val systemProperties: Map<String, String> = emptyMap()
    internal abstract fun configPath(): Path

    internal abstract fun getCryptoService(x500Principal: X500Principal, config: Path): CryptoService
    internal abstract fun cryptoServiceName(): String
    internal abstract fun deleteExistingEntries()

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    protected val portAllocation = incrementalPortAllocation()

    @Rule
    @JvmField
    val doorman = TestDoorman(portAllocation)

    companion object {
        internal val notaryName = CordaX500Name("NotaryService", "Zurich", "CH")
        internal val aliceName = CordaX500Name("Alice", "London", "GB")
        internal val genevieveName = CordaX500Name("Genevieve", "London", "GB")

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(notaryName, aliceName, genevieveName)
    }

    @Test
    fun `node registration with one node backed by HSM`() {

        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://${doorman.serverHostAndPort}"),
                null,
                publishNotaries = { doorman.server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)
        internalDriver(
                systemProperties = systemProperties,
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
                            "cryptoServiceName" to cryptoServiceName(),
                            "cryptoServiceConf" to configPath().toFile().absolutePath
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
            val cryptoService = getCryptoService(aliceName.x500Principal, configPath())
            val alicePubKey = cryptoService.getPublicKey("identity-private-key")
            assertThat(alicePubKey).isNotNull()
            assertThat(result.stx.sigs.map { it.by.encoded!! }.filter { it.contentEquals(alicePubKey!!.encoded) }).hasSize(1)
            assertThat(result.stx.sigs.single { it.by.encoded!!.contentEquals(alicePubKey!!.encoded) }.isValid(result.stx.id))
        }
    }
    @After
    fun after() {
        deleteExistingEntries()
    }
}

/**
 * When we generate and store a private key in the HSM, we still have a file-based node key store that stores the certificates.
 * We store the certificates along with an [AliasPrivateKey] as the corresponding private key entry. With this utility we demonstrate
 * that this private key entry does indeed not contain any actual private key material.
 */
internal fun ensurePrivateKeyIsNotInKeyStoreFile(alias: String, nodeKeyStore: Path, keyStorePassword: String = DEV_CA_KEY_STORE_PASS) {
    val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
    keyStore.load(nodeKeyStore.inputStream(), keyStorePassword.toCharArray())

    // The private key from the file.
    val privateKey = keyStore.getKey(alias, keyStorePassword.toCharArray())

    // a new private key that should be identical.
    val aliasPrivateKey = PKCS8Key.parseKey(DerValue(AliasPrivateKey(alias).encoded))

    assertTrue(aliasPrivateKey.encoded.contentEquals(privateKey.encoded))
    // comparing the output of getEncoded() is not sufficient, because it is not necessarily a simple getter. Therefore we access the
    // actual fields that contain relevant data and make sure they are identical.
    assertTrue(privateKey.declaredField<ByteArray>("encodedKey").value.contentEquals(aliasPrivateKey.declaredField<ByteArray>("encodedKey").value))
    assertTrue(privateKey.declaredField<ByteArray>("key").value.contentEquals(aliasPrivateKey.declaredField<ByteArray>("key").value))
    assertEquals(privateKey.declaredField<AlgorithmId>("algid").value, aliasPrivateKey.declaredField<AlgorithmId>("algid").value)

    // Demonstrate that signing is not possible
    val ecSignature = Signature.getInstance("SHA256withECDSA")
    assertFailsWith<InvalidKeyException> { ecSignature.initSign(privateKey as PrivateKey) }
    // We don't really know what type of key it is at this point
    val rsaSignature = Signature.getInstance("SHA256withRSA")
    assertFailsWith<InvalidKeyException> { rsaSignature.initSign(privateKey as PrivateKey) }
}