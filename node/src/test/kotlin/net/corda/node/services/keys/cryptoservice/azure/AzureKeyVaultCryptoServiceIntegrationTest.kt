package net.corda.node.services.keys.cryptoservice.azure

import net.corda.core.crypto.Crypto
import net.corda.core.identity.Party
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import org.junit.Ignore
import org.junit.Test
import java.time.Duration
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/*
 * These tests need to be run manually. They require an Amazon KeyVault to be set up and will perform operations that are not free of charge.
 * Refer to https://docs.microsoft.com/en-gb/azure/key-vault/
 */
@Ignore
class AzureKeyVaultCryptoServiceIntegrationTest {

    // you need to change these values to point to your KeyVault
    private val clientId = "a3d73987-c666-4bc2-9cba-b0b27c63800e"
    // creating hardware-secured keys requires a KeyVault with a Premium subscription
    private val premiumVault = "https://testkeyvault1261premium.vault.azure.net/"
    private val path = javaClass.getResource("out.pkcs12").toURI().path
    private val vaultURL = "https://mytestkeyvault1261.vault.azure.net/"

    @Test
    fun `Generate P-256 ECDSA key with hardware protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256.schemeNumberID)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA key with software protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256.schemeNumberID)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA K1 key with hardware protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256.schemeNumberID)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA K1 key with software protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256.schemeNumberID)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate RSA key with hardware protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, premiumVault, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256.schemeNumberID)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate RSA key with software protection, sign and verify data`() {
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256.schemeNumberID)
        assertNotNull(generated)
        val data = UUID.randomUUID().toString().toByteArray()
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Content signer works with X509Utilities`() {
        val path = javaClass.getResource("out.pkcs12").toURI().path
        val keyVaultClient = AzureKeyVaultCryptoService.createKeyVaultClient(path, "", "1", clientId)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = UUID.randomUUID().toString()
        val pubKey = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256.schemeNumberID)
        val signer = cryptoService.getSigner(alias)

        val otherAlias = UUID.randomUUID().toString()
        val otherPubKey = cryptoService.generateKeyPair(otherAlias, Crypto.ECDSA_SECP256R1_SHA256.schemeNumberID)
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
        assertFailsWith<IllegalArgumentException> { cryptoService.generateKeyPair("no", Integer.MIN_VALUE) }
    }
}