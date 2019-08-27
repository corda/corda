package net.corda.node.services.keys.cryptoservice.utimaco

import net.corda.core.crypto.Crypto
import net.corda.core.identity.Party
import net.corda.core.internal.toPath
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.hsm.HsmSimulator
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.junit.ClassRule
import org.junit.Test
import org.junit.runners.model.Statement
import java.io.IOException
import java.time.Duration
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtimacoCryptoServiceIntegrationTest {

    companion object {
        @ClassRule
        @JvmField
        val hsmSimulator: HsmSimulator = HsmSimulator(incrementalPortAllocation())
    }

    private val config = testConfig(hsmSimulator.address.port)
    private val login = UtimacoCryptoService.UtimacoCredentials("INTEGRATION_TEST", "INTEGRATION_TEST".toByteArray())

    @Test
    fun `When credentials are incorrect, should throw UtimacoHSMException`() {
        val config = testConfig(hsmSimulator.address.port)
        assertFailsWith<UtimacoCryptoService.UtimacoHSMException> {
            UtimacoCryptoService.fromConfig(config) {
                UtimacoCryptoService.UtimacoCredentials("invalid", "invalid".toByteArray())
            }
        }
    }

    @Test
    fun `When credentials become incorrect, should throw UtimacoHSMException`() {
        var pw = "INTEGRATION_TEST"
        val cryptoService = UtimacoCryptoService.fromConfig(config) { UtimacoCryptoService.UtimacoCredentials("INTEGRATION_TEST", pw.toByteArray()) }
        cryptoService.logOff()
        pw = "foo"
        assertFailsWith<UtimacoCryptoService.UtimacoHSMException> { cryptoService.generateKeyPair("foo", cryptoService.defaultIdentitySignatureScheme()) }
    }

    @Test
    fun `When connection cannot be established, should throw ConnectionException`() {
        val invalidConfig = testConfig(1)
        assertFailsWith<IOException> {
            UtimacoCryptoService.fromConfig(invalidConfig) { login }
        }
    }

    @Test
    fun `When alias contains illegal characters, should throw `() {
        val cryptoService = UtimacoCryptoService.fromConfig(config) { login }
        val alias = "a".repeat(257)
        assertFailsWith<UtimacoCryptoService.UtimacoHSMException> { cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256) }
    }

    @Test
    fun `Handles re-authentication properly`() {
        val cryptoService = UtimacoCryptoService.fromConfig(config) { login }
        val alias = UUID.randomUUID().toString()
        cryptoService.logOff()
        val pubKey = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        cryptoService.logOff()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(pubKey, signed, data)
    }

    @Test
    fun `Generate ECDSA key with r1 curve, then sign and verify data`() {
        val cryptoService = UtimacoCryptoService.fromConfig(config) { login }
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(pubKey, signed, data)
    }

    @Test
    fun `Generate key with the default legal identity scheme, then sign and verify data`() {
        val cryptoService = UtimacoCryptoService.fromConfig(config) { login }
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(pubKey, signed, data)
    }

    @Test
    fun `Generate ECDSA key with k1 curve, then sign and verify data`() {
        val cryptoService = UtimacoCryptoService.fromConfig(config) { login }
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256)
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(pubKey, signed, data)
    }

    @Test
    fun `Generate RSA key, then sign and verify data`() {
        val cryptoService = UtimacoCryptoService.fromConfig(config) { login }
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        assertTrue { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(pubKey, signed, data)
    }

    @Test
    fun `When key does not exist, signing should throw`() {
        val cryptoService = UtimacoCryptoService.fromConfig(config) { login }
        val alias = UUID.randomUUID().toString()
        assertFalse { cryptoService.containsKey(alias) }
        val data = UUID.randomUUID().toString().toByteArray()
        assertFailsWith<CryptoServiceException> { cryptoService.sign(alias, data) }
    }

    @Test
    fun `When key does not exist, getPublicKey should return null`() {
        val cryptoService = UtimacoCryptoService.fromConfig(config) { login }
        val alias = UUID.randomUUID().toString()
        assertFalse { cryptoService.containsKey(alias) }
        assertNull(cryptoService.getPublicKey(alias))
    }

    @Test
    fun `When key does not exist, getContentSigner should throw`() {
        val cryptoService = UtimacoCryptoService.fromConfig(config)
        { UtimacoCryptoService.UtimacoCredentials("INTEGRATION_TEST", "INTEGRATION_TEST".toByteArray()) }
        val alias = UUID.randomUUID().toString()
        assertFalse { cryptoService.containsKey(alias) }
        assertFailsWith<CryptoServiceException> { cryptoService.getSigner(alias) }
    }

    @Test
    fun `Content signer works with X509Utilities`() {
        val cryptoService = UtimacoCryptoService.fromConfig(config) { login }
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        val signer = cryptoService.getSigner(alias)
        val otherAlias = UUID.randomUUID().toString()
        val otherPubKey = cryptoService.generateKeyPair(otherAlias, cryptoService.defaultIdentitySignatureScheme())
        val issuer = Party(DUMMY_BANK_A_NAME, pubKey)
        val partyAndCert = getTestPartyAndCertificate(issuer)
        val issuerCert = partyAndCert.certificate
        val window = X509Utilities.getCertificateValidityWindow(Duration.ZERO, 3650.days, issuerCert)
        val ourCertificate = X509Utilities.createCertificate(
                CertificateType.CONFIDENTIAL_LEGAL_IDENTITY,
                issuerCert.subjectX500Principal,
                issuerCert.publicKey,
                signer,
                partyAndCert.name.x500Principal,
                otherPubKey,
                window)
        ourCertificate.checkValidity()
    }

    @Test
    fun `login with key file`() {
        // the admin user of the simulator is set up with key-file login
        val keyFile = UtimacoCryptoServiceIntegrationTest::class.java.getResource("ADMIN.keykey").toPath()
        val username = "ADMIN"
        val pw = "utimaco".toByteArray()
        val conf = config.copy(authThreshold = 0) // because auth state for the admin user is 570425344
        // the admin user does not have permission to access or create keys, so this operation will fail
        assertFailsWith<UtimacoCryptoService.UtimacoHSMException> {
            // Exception can be thrown from either of the following methods depending on if HSM cluster is used or not
            val cryptoService = UtimacoCryptoService.fromConfig(conf) { UtimacoCryptoService.UtimacoCredentials(username, pw, keyFile) }
            cryptoService.generateKeyPair("no", cryptoService.defaultIdentitySignatureScheme())
        }
    }

    @Test
    fun `Handles re-connection properly`() {
        lateinit var cryptoService: UtimacoCryptoService
        val generateKeysAndSign = {
            val alias = UUID.randomUUID().toString()
            val pubKey = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
            assertTrue { cryptoService.containsKey(alias) }
            val data = UUID.randomUUID().toString().toByteArray()
            val signed = cryptoService.sign(alias, data)
            Crypto.doVerify(pubKey, signed, data)
        }

        // Cannot run before() and after() on a TestRule directly
        fun HsmSimulator.execute(block: () -> Unit) = apply(object : Statement() {
            override fun evaluate() = block()
        }, null).evaluate()

        // Start HSM simulator first time and init CryptoService
        val hsmSimulatorWithRestart = HsmSimulator(incrementalPortAllocation(12300))
        hsmSimulatorWithRestart.execute {
            cryptoService = UtimacoCryptoService.fromConfig(testConfig(hsmSimulatorWithRestart.address.port), login)
            generateKeysAndSign()
        }

        // HSM is down
        assertFailsWith<UtimacoCryptoService.UtimacoHSMException> { generateKeysAndSign() }

        // Start HSM simulator second time, reuse CryptoService
        hsmSimulatorWithRestart.execute { generateKeysAndSign() }
    }
}