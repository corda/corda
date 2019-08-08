package net.corda.node.services.keys.cryptoservice.azure

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.internal.Instances
import net.corda.core.crypto.internal.cordaBouncyCastleProvider
import net.corda.core.identity.Party
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.azure.AzureKeyVaultCryptoService
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import org.junit.Ignore
import org.junit.Test
import java.security.PublicKey
import java.security.SignatureException
import java.time.Duration
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/*
 * These tests need to be run manually. They require an Amazon KeyVault (and the associated service principal) to be set up and will perform operations that are not free of charge.
 * Refer to https://docs.microsoft.com/en-gb/azure/key-vault/
 *
 * This can be done using the scripts available under the resources folder.
 * The only pre-requisites are:
 * - Install the azure CLI and execute `az login` (See: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli)
 * - Install jq (See: https://stedolan.github.io/jq)
 *
 * To execute the test:
 * - Navigate to `resources/net/corda/node/services/key/cryptoservice/azure` and execute `setup_resources.sh`, which creates all the necessary resources.
 * - Replace the variable `clientId` with the value provided in the output of the previous script.
 * - Run the tests
 * - In the end, navigate to `resources/net/corda/node/services/key/cryptoservice/azure` and execute `tear_down_resources.sh`, which removes all the created resources.
 *
 */
@Ignore
class AzureKeyVaultCryptoServiceTest {

    // you need to change these values to point to your KeyVault
    private val clientId = "<the-client-id>"
    // creating hardware-secured keys requires a KeyVault with a Premium subscription
    private val premiumVault = "https://premium-corda-keyvault.vault.azure.net/"
    private val path = javaClass.getResource("out.pkcs12").toURI().path
    private val vaultURL = "https://standard-corda-keyvault.vault.azure.net/"

    @Test
    fun `Generate key with the default legal identity scheme, then sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA key with hardware protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA key with hardware protection, sign with SHA384WITHECDSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA384WITHECDSA")
        verifySignature("SHA384WITHECDSA", generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA key with hardware protection, sign with SHA256WITHECDSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA256WITHECDSA")
        verifySignature("SHA256WITHECDSA", generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA key with hardware protection, sign with SHA512WITHECDSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA512WITHECDSA")
        verifySignature("SHA512WITHECDSA", generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA key with software protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA key with software protection, sign with SHA512WITHECDSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA512WITHECDSA")
        verifySignature("SHA512WITHECDSA", generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA K1 key with hardware protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA K1 key with hardware protection, sign with SHA512WITHECDSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA512WITHECDSA")
        verifySignature("SHA512WITHECDSA", generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA K1 key with software protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA K1 key with software protection, sign with SHA512WITHECDSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA512WITHECDSA")
        verifySignature("SHA512WITHECDSA", generated, signed, data)

    }

    @Test
    fun `Generate RSA key with hardware protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate RSA key with hardware protection, sign with SHA512WITHRSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA512WITHERSA")
        verifySignature("SHA512WITHRSA", generated, signed, data)
    }

    @Test
    fun `Generate RSA key with hardware protection, sign with SHA384WITHRSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA384WITHERSA")
        verifySignature("SHA384WITHRSA", generated, signed, data)
    }

    @Test
    fun `Generate RSA key with hardware protection, sign with SHA256WITHRSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA256WITHERSA")
        verifySignature("SHA256WITHRSA", generated, signed, data)
    }

    @Test
    fun `Generate RSA key with software protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate RSA key with software protection, sign with SHA512WITHRSA and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data, "SHA512WITHRSA")
        verifySignature("SHA512WITHRSA", generated, signed, data)
    }

    @Test
    fun `Content signer works with X509Utilities`() {
        val path = javaClass.getResource("out.pkcs12").toURI().path
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
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
    fun `When the key is not in the vault, contains should return false`() {
        val path = javaClass.getResource("out.pkcs12").toURI().path
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val exists = cryptoService.containsKey("nothing")
        assertFalse(exists)
    }

    @Test
    fun `When the key is not in the vault, getPublicKey should return null`() {
        val path = javaClass.getResource("out.pkcs12").toURI().path
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        assertNull(cryptoService.getPublicKey("nothing"))
    }

    @Test
    fun `When the schemeId is not supported generateKeyPair should throw IAE`() {
        val path = javaClass.getResource("out.pkcs12").toURI().path
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        assertFailsWith<IllegalArgumentException> { cryptoService.generateKeyPair("no", Crypto.EDDSA_ED25519_SHA512) }
    }
}

private fun verifySignature(signatureName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
    val signature = Instances.getSignatureInstance(signatureName, cordaBouncyCastleProvider)
    signature.initVerify(publicKey)
    signature.update(clearData)
    return if (signature.verify(signatureData)) true else throw SignatureException("Signature Verification failed!")
}