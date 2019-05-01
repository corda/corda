package net.corda.nodeapi.internal.cryptoservice.azure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.azure.keyvault.KeyVaultClient
import com.microsoft.azure.keyvault.models.KeyBundle
import com.microsoft.azure.keyvault.models.KeyOperationResult
import com.microsoft.azure.keyvault.webkey.JsonWebKey
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.times
import net.corda.core.crypto.Crypto
import net.corda.core.identity.Party
import net.corda.core.internal.toPath
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import org.junit.Test
import org.mockito.Mockito
import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.*
import kotlin.test.*

/**
 * The values used for mocking the KeyVaultClient were obtained by manually running [AzureKeyVaultCryptoServiceTest] and intercepting
 * what the service returned.
 */
class AzureKeyVaultCryptoServiceTest {

    private val vaultURL = "none"
    private val objectMapper = ObjectMapper()

    @Test
    fun `Generate key with the default legal identity scheme, then sign and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL)
        val alias = "c16d2aa8-2f42-4b2f-946d-00e51df43d88"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC-HSM\",\"key_ops\":[\"sign\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256\",\"x\":\"Q9sEtdtbb2tb2XJHXtgD80BO5RcUpL3Q2xoca7CQZ7E\",\"y\":\"eDZP2RTd6_Nyk-uJ7Zs6MDaxzQ2RtQZJyWVXE15Dflg\"}"
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val exists = cryptoService.containsKey(alias)
        assertTrue(exists)
        val data = Base64.getDecoder().decode("NmU1ZTMxZWYtYWU0NS00MzM0LThkYjQtNzRkNWU3NDQ1YWY3")
        val resultString = "{\"kid\":\"none\",\"value\":\"nvrVKgmfDa3MSAStp4PfiB9VkPJfLVR-m7hzgGYpL2qcoI2QxF4H54dkMC798ff-3YLqcHN6PX7v3nxvJR-QYg\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data)
        // doesn't throw
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate key with the default legal identity scheme, then sign using SHA512withRSA and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL)
        val alias = "c16d2aa8-2f42-4b2f-946d-00e51df43d88"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC-HSM\",\"key_ops\":[\"sign\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256\",\"x\":\"Q9sEtdtbb2tb2XJHXtgD80BO5RcUpL3Q2xoca7CQZ7E\",\"y\":\"eDZP2RTd6_Nyk-uJ7Zs6MDaxzQ2RtQZJyWVXE15Dflg\"}"
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val exists = cryptoService.containsKey(alias)
        assertTrue(exists)
        val data = Base64.getDecoder().decode("NmU1ZTMxZWYtYWU0NS00MzM0LThkYjQtNzRkNWU3NDQ1YWY3")
        val resultString = "{\"kid\":\"none\",\"value\":\"nvrVKgmfDa3MSAStp4PfiB9VkPJfLVR-m7hzgGYpL2qcoI2QxF4H54dkMC798ff-3YLqcHN6PX7v3nxvJR-QYg\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data, "SHA512withRSA")
        // doesn't throw
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate key with the default legal identity scheme, then sign using MD5withRSA and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL)
        val alias = "c16d2aa8-2f42-4b2f-946d-00e51df43d88"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC-HSM\",\"key_ops\":[\"sign\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256\",\"x\":\"Q9sEtdtbb2tb2XJHXtgD80BO5RcUpL3Q2xoca7CQZ7E\",\"y\":\"eDZP2RTd6_Nyk-uJ7Zs6MDaxzQ2RtQZJyWVXE15Dflg\"}"
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val exists = cryptoService.containsKey(alias)
        assertTrue(exists)
        val data = Base64.getDecoder().decode("NmU1ZTMxZWYtYWU0NS00MzM0LThkYjQtNzRkNWU3NDQ1YWY3")
        val resultString = "{\"kid\":\"none\",\"value\":\"nvrVKgmfDa3MSAStp4PfiB9VkPJfLVR-m7hzgGYpL2qcoI2QxF4H54dkMC798ff-3YLqcHN6PX7v3nxvJR-QYg\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data, "MD5withRSA")
        // doesn't throw
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate key with the default legal identity scheme, then sign using SHA512withECDSA and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL)
        val alias = "c16d2aa8-2f42-4b2f-946d-00e51df43d88"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC-HSM\",\"key_ops\":[\"sign\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256\",\"x\":\"Q9sEtdtbb2tb2XJHXtgD80BO5RcUpL3Q2xoca7CQZ7E\",\"y\":\"eDZP2RTd6_Nyk-uJ7Zs6MDaxzQ2RtQZJyWVXE15Dflg\"}"
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val exists = cryptoService.containsKey(alias)
        assertTrue(exists)
        val data = Base64.getDecoder().decode("NmU1ZTMxZWYtYWU0NS00MzM0LThkYjQtNzRkNWU3NDQ1YWY3")
        val resultString = "{\"kid\":\"none\",\"value\":\"nvrVKgmfDa3MSAStp4PfiB9VkPJfLVR-m7hzgGYpL2qcoI2QxF4H54dkMC798ff-3YLqcHN6PX7v3nxvJR-QYg\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data, "SHA512withECDSA")
        // doesn't throw
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate key with the default legal identity scheme, then sign using SHA512WITHECDSA (checking case) and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL)
        val alias = "c16d2aa8-2f42-4b2f-946d-00e51df43d88"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC-HSM\",\"key_ops\":[\"sign\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256\",\"x\":\"Q9sEtdtbb2tb2XJHXtgD80BO5RcUpL3Q2xoca7CQZ7E\",\"y\":\"eDZP2RTd6_Nyk-uJ7Zs6MDaxzQ2RtQZJyWVXE15Dflg\"}"
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val exists = cryptoService.containsKey(alias)
        assertTrue(exists)
        val data = Base64.getDecoder().decode("NmU1ZTMxZWYtYWU0NS00MzM0LThkYjQtNzRkNWU3NDQ1YWY3")
        val resultString = "{\"kid\":\"none\",\"value\":\"nvrVKgmfDa3MSAStp4PfiB9VkPJfLVR-m7hzgGYpL2qcoI2QxF4H54dkMC798ff-3YLqcHN6PX7v3nxvJR-QYg\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data, "SHA512WITHECDSA")
        // doesn't throw
        Crypto.doVerify(generated, signed, data)
    }

    @Test(expected=IllegalArgumentException::class)
    fun `Exception raised when sign string does not contain with string`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL)
        val alias = "c16d2aa8-2f42-4b2f-946d-00e51df43d88"
        val data = Base64.getDecoder().decode("NmU1ZTMxZWYtYWU0NS00MzM0LThkYjQtNzRkNWU3NDQ1YWY3")
        cryptoService.sign(alias, data, "SHA512ECDSA")
    }

    @Test
    fun `Generate P-256 ECDSA key with hardware protection, sign and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = "c16d2aa8-2f42-4b2f-946d-00e51df43d88"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC-HSM\",\"key_ops\":[\"sign\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256\",\"x\":\"Q9sEtdtbb2tb2XJHXtgD80BO5RcUpL3Q2xoca7CQZ7E\",\"y\":\"eDZP2RTd6_Nyk-uJ7Zs6MDaxzQ2RtQZJyWVXE15Dflg\"}"
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val exists = cryptoService.containsKey(alias)
        assertTrue(exists)
        val data = Base64.getDecoder().decode("NmU1ZTMxZWYtYWU0NS00MzM0LThkYjQtNzRkNWU3NDQ1YWY3")
        val resultString = "{\"kid\":\"none\",\"value\":\"nvrVKgmfDa3MSAStp4PfiB9VkPJfLVR-m7hzgGYpL2qcoI2QxF4H54dkMC798ff-3YLqcHN6PX7v3nxvJR-QYg\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data)
        // doesn't throw
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256 ECDSA key with software protection, sign and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = "19fdad99-fc82-4d0a-bea3-4c71395f880e"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC\",\"key_ops\":[\"sign\",\"verify\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256\",\"x\":\"7rFaNa9LyFGYDxk6QSPWNTyq0XjqOIIWgxuTBPzU2R8\",\"y\":\"NEPAf9aZYbyYDa42Xjq6QvGlfX7nEew7Fs-AO3vYrNg\"}"
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val exists = cryptoService.containsKey(alias)
        assertTrue(exists)
        val data = Base64.getDecoder().decode("ZTRjYmMyNWQtZDhmYi00M2MyLWIwZWYtOTFhYzM2YzM0Nzdm")
        val resultString = "{\"kid\":\"none\",\"value\":\"EMrnqe0JNgbPp2A420Tlaiqeh0w45HNIrL62isRXcnbrPs9YnfiCyJ2ubYn-KgvmrHr-xs1Q9AqYkIAJEW-8JQ\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256K ECDSA key with hardware protection, sign and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val vaultURL = "https://nope"
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = "ef2b87da-db6f-406a-b69b-3ed24f80abdb"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC-HSM\",\"key_ops\":[\"sign\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256K\",\"x\":\"tjHBJXJ0G3qcUpThhb5fShKTJKB-rWoHaqhBVG7aZtQ\",\"y\":\"5UcAc_okBnbJRG5vzzFGiFq7I-t4pIyHiVvufQkLjlA\"}"
        val objectMapper = ObjectMapper()
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256)
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val data = Base64.getDecoder().decode("M2M0ODFlZjQtN2FhYS00MzJlLTg5MjgtMDQ1OWVmYTI2YTU2")
        val resultString = "{\"kid\":\"none\",\"value\":\"HNKQKM75ip-BQiDysK3wP9uxYwUxVH_m8OpG4mXOfAhxa9c-3n_lqJmoe7nHj1ebBt5dXurXO1UhKdg7xw36og\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate P-256K ECDSA key with software protection, sign and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val vaultURL = "https://nope"
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = "23f7e197-e8ae-4a57-b328-0a9a6c008e05"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC\",\"key_ops\":[\"sign\",\"verify\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256K\",\"x\":\"Vk7oIWrB3N2cnzAPej1-d_8UkSiRsZPgb4Cg98p-U4I\",\"y\":\"lngidTj8RU-iIIZARZ_T8C71qCR-ukFsovH1HdI5Sn8\"}"
        val objectMapper = ObjectMapper()
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256K1_SHA256)
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val data = Base64.getDecoder().decode("N2RiYjdmMDEtYmVkNS00Y2MxLThhNDQtYWIwNDQzMDRlYmQ0")
        val resultString = "{\"kid\":\"none\",\"value\":\"Lo-FW2f2Voj7as4zLHJeY-oQ412ma30eMJKXAqQTLUHSG4qEXtFHIMn35b_fpv8kc7-heocp2M6XbhNRHA-5uA\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate RSA key with hardware protection, sign and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val vaultURL = "https://nope"
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.HARDWARE)
        val alias = "f64e3bf0-da8e-4470-b80a-b0072f2f0688"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"RSA-HSM\",\"key_ops\":[\"sign\"],\"n\":\"6rpVAz49zNP6kFCyrtDU3LZDFnDIT5haqpRuSUn_osE5Z08ZQkbTypT_yXUI11QUzr9VHZ4rPuXyh_uyNo_e4eswpkpfV3za5ynvjA_zn9Nfnj9otD4c18Vf3OsoRofGAary-TiOkNDEy67ikdi6-lmU613HjeUN8QJJ_jSdh8F1M8D33yKhivGZORxvX_Lab0ge5qwRAY9SL7MKomPm4HEXJvbDKQLjOtRDyvePtpuzmxEjp8Zbb0T323hFxWMSvzuNSXuYtuWVzNAtUEisldEQIenPJrCWHqI3UbworqhvGl6cEScILGum-olylvZW6fd5XsL2jmJgNp0043cH_HrjVsUtNU_EdZTpFplFVu2wXqZeAChO34VZ6LwnDqKeZbvuzYhrieXL1zcWs2w4fR_-ICBrE0aYZUFB-qyqTWt3wa-dcJv7HKjIwBOK2nT4zzOeOR36HKzGd_mBKNyOY0Sc0O9Jg6Xi3M4mLp6ipnyKqbQg6DvnTOmObjajkXqx\",\"e\":\"AAEAAQ\",\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":null,\"x\":null,\"y\":null}"
        val objectMapper = ObjectMapper()
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val data = Base64.getDecoder().decode("NzNhNjhmNTgtYzJkOS00YTAyLWJiZmYtYzZiOGNmMjI3Zjk2")
        val resultString = "{\"kid\":\"none\",\"value\":\"i6wWbOjFI5BLXF_iNbOw4bYeD3gnWIbx6_jhttTRD4bPTFq4GJlapLN1mYH9S0p0oDVpoXfAOYEzrL4dpVtN5viY_Tm1nVKO0eFNqgnSmNDl6h4PjE8cj5Jf1WnpAdoY1cz57j6tjQNrBAV-vCtHwfZSRfRVxwcs0Qm5LhL-FgWhvBint-2ROdwtY1g6yWy_8EvkqFN0npcjn8z4ggZA2nqzEFjW102mNlcTIFxXGFs9LCrsB8MKY4ve4psgsdNTmv30dK8LGeoOYf8SVPMdnR2fLe55X7hnACUAvD8YmeIvXVxtlF_xXCOAuyXrjbSc5od4RrjymfO_1z5ivwmKpxDLJXIs8-BaOmZdpF10RPQ6aq3EtRVZTiDd00Gq1DA3qUUvxCXWnU9dY0pwQBdtRSa5F9We7RoTLTI015ZCx6zDEOpT9-HMlD4S84deT1m2u--Xyo53HoloJSbXMmj6nGW7_GBuQxERozD5cNwVgks6V9ufROYYnPdzJZyc2h2Y\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Generate RSA key with software protection, sign and verify data`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val vaultURL = "https://nope"
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = "17d668fd-7277-44c6-b570-a5751e4ccde3"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"RSA-HSM\",\"key_ops\":[\"sign\"],\"n\":\"vXSPsJTkek-MTTloTP8j84rFVImXKPQXuqELbVKL2bd5DHJXBHHTZunBxSx9Q4UhH8yBsVmgcrn8orbFioNDrmPeeoFUKbG9yZIqsHPXUJgbf1Iv2RUZ9pPYxgyzwRBFiiZs2oAlXTj5iRLdizkYFePhnAZP0MV9WpZhCz0U6Tvc8AVPbyCuOU1Kyq2fHAR1tYMFeS88Q5y3dLUcXReVbQZ9WWYh6QNbyfjYJ91w8Bpso4YW2UsskEsnUDvBfeVUvajEWMYhWBcVHwYlETh_NG36sDOY42YCQ8pqd0fmDTHQDFn09uwxXLldCziFPWgss1z0tFs2ztsYEx5V_yYZSBQfpgcJOA36RrpWKgMs2J8Wb6jioGeoKgLEpcvgKb_EFXRBMM_E9iJGm_WIClp0Pir44GCmCHgTWiEgx5F5FTGrjcPrkU-XzrJZ2thEkx2mMqKheixFbS7_Qr0bJCgCf6rcDOZls_SZl0qXhlN7N9Y6cVSYQRM8Jj-pemzaUgLX\",\"e\":\"AAEAAQ\",\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":null,\"x\":null,\"y\":null}"
        val objectMapper = ObjectMapper()
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val generated = cryptoService.generateKeyPair(alias, Crypto.RSA_SHA256)
        Mockito.verify(keyVaultClient, times(1)).createKey(any())
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val data = Base64.getDecoder().decode("ZWMwNjVjYjctYmFhNy00YjhkLWE1ZDQtNGZjMmQxYmI2OWY4")
        val resultString = "{\"kid\":\"none\",\"value\":\"kyXAs9MK3XYEQIWxar8UqyjxRIvHJfzXsX_Ahy1zrwNbeReOXYItz8zN0oT5x6CHDQ69IHdnF72y2SRrH8WvTqqxKC2Wqh0v8JO-qqaz6hAtKb7ziwndEUp4EBwFOLTfSLk61N9c7BNsQcidl8XfjrGG91oWC0sy6mhiHNq7molRtIU6R-wM77w9e5ablEbs0IKd2iMzn0lB8FvW0vJfEBqPTAgCWY5aUtvQfew5Ej3FwoJlJqVSg1yFZc84lmtIFZY7IDgrKOldjeQVowgqPA0bhbhHNHERZtbl7hZ6oMlLKvEKdmfGbcckSyCzULOTC0MSATJYogTro4aqk02jIWztO2J_XwjNo62wL782AwBi9J5Z-0F1TtndWeHluVuj5gRjGeRkhySVPa0Rh6OIS5kRxyQazAGnBjZ80HenXRr8fwxwVz6PGAvCYTthR0W5maCrpWLIijs663vimvhGxFXjtc85O_7XZZYUV6YZbcH9z4P6k3yrs2cJrS24H58z\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)
        val signed = cryptoService.sign(alias, data)
        Crypto.doVerify(generated, signed, data)
    }

    @Test
    fun `Content signer works with X509Utilities`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL, AzureKeyVaultCryptoService.Protection.SOFTWARE)
        val alias = "951990e4-a86f-4f9d-bff8-8d34bb64c792"
        val webKeyString = "{\"kid\":\"none\",\"kty\":\"EC\",\"key_ops\":[\"sign\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256\",\"x\":\"jj2KEEaO5WzwB6FEDhxGk1wRUvql34Obs9vOOMJvnl4\",\"y\":\"SgzL1qbL3YoyMZkkJe_FX54sxV9AQV_QbeYMEt_vdQ0\"}"
        val jsonWebKey = objectMapper.readValue<JsonWebKey>(webKeyString)
        val keyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(keyBundle.key()).thenReturn(jsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(keyBundle)
        val pubKey = cryptoService.generateKeyPair(alias, Crypto.ECDSA_SECP256R1_SHA256)
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(keyBundle)
        val signer = cryptoService.getSigner(alias)

        val otherAlias = "4602b9d4-4e0e-4956-aa93-aa091480d7c7"
        val otherWebKeyString = "{\"kid\":\"none\",\"kty\":\"EC\",\"key_ops\":[\"sign\"],\"n\":null,\"e\":null,\"d\":null,\"dp\":null,\"dq\":null,\"qi\":null,\"p\":null,\"q\":null,\"k\":null,\"key_hsm\":null,\"crv\":\"P-256\",\"x\":\"UIrB2wluAJlDnQCFtwM5AUBPbdDqQeQ2dVlJPcusbes\",\"y\":\"NyTF4r03wnmboUoNhLTRs0gUgmRTnF1rRffxACfQdEc\"}"
        val otherJsonWebKey = objectMapper.readValue<JsonWebKey>(otherWebKeyString)
        val otherKeyBundle = Mockito.mock(KeyBundle::class.java)
        Mockito.`when`(otherKeyBundle.key()).thenReturn(otherJsonWebKey)
        Mockito.`when`(keyVaultClient.createKey(any())).thenReturn(otherKeyBundle)
        val otherPubKey = cryptoService.generateKeyPair(otherAlias, Crypto.ECDSA_SECP256R1_SHA256)

        val resultString = "{\"kid\":\"none\",\"value\":\"nr3k8MvlqN2ycg-gJmRyS9VKCEffh4d8FKBqqi9UV2R6MKrpTokc4R1_8lBRBC9JLweRz6V--PKQPu4i8WH9NA\"}"
        val result = objectMapper.readValue<KeyOperationResult>(resultString)
        Mockito.`when`(keyVaultClient.sign(any(), any(), any())).thenReturn(result)

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
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, "")
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(null)
        val exists = cryptoService.containsKey("nothing")
        assertFalse(exists)
    }

    @Test
    fun `When the key is not in the vault, getPublicKey should return null`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, "")
        Mockito.`when`(keyVaultClient.getKey(any())).thenReturn(null)
        assertNull(cryptoService.getPublicKey("nothing"))
    }

    @Test
    fun `When the schemeId is not supported generateKeyPair should throw IAE`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val vaultURL = "https://nope"
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL)
        assertFailsWith<IllegalArgumentException> { cryptoService.generateKeyPair("no", Crypto.EDDSA_ED25519_SHA512) }
    }

    @Test
    fun `Parse config file`() {
        val config = AzureKeyVaultCryptoService.parseConfigFile(javaClass.getResource("azkv.conf").toPath())
        assertEquals(AzureKeyVaultCryptoService.Protection.HARDWARE, config.protection)
    }


    @Test
    fun `When alias is invalid, should throw IAE`() {
        val keyVaultClient: KeyVaultClient = Mockito.mock(KeyVaultClient::class.java)
        val vaultURL = "https://nope"
        val cryptoService = AzureKeyVaultCryptoService(keyVaultClient, vaultURL)
        val withIllegalChar = "asdf!-d"
        val tooLong = "a".repeat(128)

        assertFailsWith<IllegalArgumentException> { cryptoService.generateKeyPair(withIllegalChar, cryptoService.defaultIdentitySignatureScheme()) }
        assertFailsWith<IllegalArgumentException> { cryptoService.generateKeyPair(tooLong, cryptoService.defaultIdentitySignatureScheme()) }

        assertFailsWith<IllegalArgumentException> { cryptoService.getPublicKey(withIllegalChar) }
        assertFailsWith<IllegalArgumentException> { cryptoService.getPublicKey(tooLong) }

        assertFailsWith<IllegalArgumentException> { cryptoService.getSigner(withIllegalChar) }
        assertFailsWith<IllegalArgumentException> { cryptoService.getSigner(tooLong) }

        assertFailsWith<IllegalArgumentException> { cryptoService.containsKey(withIllegalChar) }
        assertFailsWith<IllegalArgumentException> { cryptoService.containsKey(tooLong) }
    }
}