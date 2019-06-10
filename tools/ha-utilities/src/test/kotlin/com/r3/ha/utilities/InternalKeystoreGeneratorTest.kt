package com.r3.ha.utilities

import io.mockk.every
import io.mockk.mockkObject
import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.internal.AliasPrivateKey
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.crypto.getCertificateAndKeyPair
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceFactory
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import org.bouncycastle.operator.ContentSigner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import java.nio.file.Path
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InternalKeystoreGeneratorTest {

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `generate tunnel keystores correctly`() {
        val generator = InternalTunnelKeystoreGenerator()
        val workingDirectory = tempFolder.root.toPath()
        val keyStorePassword = "keyStorePassword"
        val entryPassword = "entryPassword"
        val trustStorePassword = "trustStorePassword"
        CommandLine.populateCommand(generator,
                BASE_DIR, workingDirectory.toString(),
                "--keyStorePassword", keyStorePassword,
                "--entryPassword", entryPassword,
                "--trustStorePassword", trustStorePassword)
        assertEquals(workingDirectory, generator.baseDirectory)
        assertEquals(keyStorePassword, generator.keyStorePassword)
        assertEquals(entryPassword, generator.entryPassword)
        assertEquals(trustStorePassword, generator.trustStorePassword)
        generator.runProgram()

        fun checkKeystore(keyStoreFile: Path, alias: String) {
            assertTrue(keyStoreFile.exists())
            assertTrue(X509KeyStore.fromFile(keyStoreFile, keyStorePassword).contains(alias))
            assertTrue(X509KeyStore.fromFile(keyStoreFile, keyStorePassword).internal.isKeyEntry(alias))
            assertNotNull(X509KeyStore.fromFile(keyStoreFile, keyStorePassword).internal.getCertificateAndKeyPair(alias, entryPassword))
        }

        checkKeystore(workingDirectory / "tunnel/float.jks", "${CORDA_CLIENT_TLS}float")
        checkKeystore(workingDirectory / "tunnel/bridge.jks", "${CORDA_CLIENT_TLS}bridge")

        X509KeyStore.fromFile(workingDirectory / "tunnel" / "tunnel-root.jks", keyStorePassword).update {
            assertTrue(contains(CORDA_ROOT_CA))
            assertTrue(internal.isKeyEntry(CORDA_ROOT_CA))
            assertNotNull(internal.getCertificateAndKeyPair(CORDA_ROOT_CA, entryPassword))
        }

        X509KeyStore.fromFile(workingDirectory / "tunnel" / "tunnel-truststore.jks", trustStorePassword).update {
            assertTrue(internal.isCertificateEntry(CORDA_ROOT_CA))
            assertFalse(internal.isKeyEntry(CORDA_ROOT_CA))
        }
    }


    @Test
    fun `generate tunnel keystores correctly when using HSM`() {
        mockkObject(CryptoServiceFactory)
        val mockedCryptoService = MockedCryptoService(tempFolder.root.toPath())
        every { CryptoServiceFactory.makeCryptoService( cryptoServiceName = any(),
                legalName = any(),
                signingCertificateStore = any(),
                cryptoServiceConf = any()) } returns mockedCryptoService

        val generator = InternalTunnelKeystoreGenerator()
        val workingDirectory = tempFolder.root.toPath()
        val keyStorePassword = "keyStorePassword"
        val entryPassword = "entryPassword"
        val trustStorePassword = "trustStorePassword"
        CommandLine.populateCommand(generator,
                BASE_DIR, workingDirectory.toString(),
                "--keyStorePassword", keyStorePassword,
                "--entryPassword", entryPassword,
                "--trustStorePassword", trustStorePassword,
                "--float-hsm-name", "a",
                "--float-hsm-config-file", "./azure.conf",
                "--bridge-hsm-name", "a",
                "--bridge-hsm-config-file", "./azure.conf")
        assertEquals(workingDirectory, generator.baseDirectory)
        assertEquals(keyStorePassword, generator.keyStorePassword)
        assertEquals(entryPassword, generator.entryPassword)
        assertEquals(trustStorePassword, generator.trustStorePassword)
        generator.runProgram()

        checkKeystore(workingDirectory / "tunnel/float.jks", "${CORDA_CLIENT_TLS}float", keyStorePassword, entryPassword)
        checkKeystore(workingDirectory / "tunnel/bridge.jks", "${CORDA_CLIENT_TLS}bridge", keyStorePassword, entryPassword)

        X509KeyStore.fromFile(workingDirectory / "tunnel" / "tunnel-root.jks", keyStorePassword).update {
            assertTrue(contains(CORDA_ROOT_CA))
            assertTrue(internal.isKeyEntry(CORDA_ROOT_CA))
            assertNotNull(internal.getCertificateAndKeyPair(CORDA_ROOT_CA, entryPassword))
        }

        X509KeyStore.fromFile(workingDirectory / "tunnel" / "tunnel-truststore.jks", trustStorePassword).update {
            assertTrue(internal.isCertificateEntry(CORDA_ROOT_CA))
            assertFalse(internal.isKeyEntry(CORDA_ROOT_CA))
        }
    }

    private fun checkKeystore(keyStoreFile: Path, alias: String, keyStorePassword: String, entryPassword: String) {
        assertTrue(keyStoreFile.exists())
        assertTrue(X509KeyStore.fromFile(keyStoreFile, keyStorePassword).contains(alias))
        assertTrue(X509KeyStore.fromFile(keyStoreFile, keyStorePassword).internal.isKeyEntry(alias))
        // The dummy private key in local file is the alias - check this.
        val certificateAndKeyPair = X509KeyStore.fromFile(keyStoreFile, keyStorePassword).internal.getCertificateAndKeyPair(alias, entryPassword)
        val privateKeyFromFile = certificateAndKeyPair.keyPair.private
        assertTrue { privateKeyFromFile is AliasPrivateKey }
        assertEquals(alias, (privateKeyFromFile as AliasPrivateKey).alias)
    }

    @Test
    fun `generate Artemis keystores correctly`() {
        val generator = InternalArtemisKeystoreGenerator()
        val workingDirectory = tempFolder.root.toPath()
        val keyStorePassword = "keyStorePassword"
        val trustStorePassword = "trustStorePassword"
        CommandLine.populateCommand(generator,
                BASE_DIR, workingDirectory.toString(),
                "--keyStorePassword", keyStorePassword,
                "--trustStorePassword", trustStorePassword)
        assertEquals(workingDirectory, generator.baseDirectory)
        assertEquals(keyStorePassword, generator.keyStorePassword)
        assertEquals(trustStorePassword, generator.trustStorePassword)
        generator.runProgram()

        listOf("artemis.jks").map { workingDirectory / "artemis" / it }.forEach {
            assertTrue(it.exists())
            assertTrue(X509KeyStore.fromFile(it, keyStorePassword).contains(CORDA_CLIENT_TLS))
            assertTrue(X509KeyStore.fromFile(it, keyStorePassword).internal.isKeyEntry(CORDA_CLIENT_TLS))
        }

        X509KeyStore.fromFile(workingDirectory / "artemis" / "artemis-root.jks", keyStorePassword).update {
            assertTrue(contains(CORDA_ROOT_CA))
            assertTrue(internal.isKeyEntry(CORDA_ROOT_CA))
        }

        X509KeyStore.fromFile(workingDirectory / "artemis" / "artemis-truststore.jks", trustStorePassword).update {
            assertTrue(contains(CORDA_ROOT_CA))
            assertFalse(internal.isKeyEntry(CORDA_ROOT_CA))
        }
    }

    @Test
    fun `generate Artemis keystores correctly when using HSM`() {

        mockkObject(CryptoServiceFactory)
        val mockedCryptoService = MockedCryptoService(tempFolder.root.toPath())
        every { CryptoServiceFactory.makeCryptoService( cryptoServiceName = any(),
                legalName = any(),
                signingCertificateStore = any(),
                cryptoServiceConf = any()) } returns mockedCryptoService

        val generator = InternalArtemisKeystoreGenerator()
        val workingDirectory = tempFolder.root.toPath()
        val keyStorePassword = "keyStorePassword"
        val trustStorePassword = "trustStorePassword"
        CommandLine.populateCommand(generator,
                BASE_DIR, workingDirectory.toString(),
                "--keyStorePassword", keyStorePassword,
                "--trustStorePassword", trustStorePassword,
                "--hsm-name", "a",
                "--hsm-config-file", "./azure.conf")
        assertEquals(workingDirectory, generator.baseDirectory)
        assertEquals(keyStorePassword, generator.keyStorePassword)
        assertEquals(trustStorePassword, generator.trustStorePassword)
        generator.runProgram()

        listOf("artemis.jks").map { workingDirectory / "artemis" / it }.forEach {
            assertTrue(it.exists())
            assertTrue(X509KeyStore.fromFile(it, keyStorePassword).contains(CORDA_CLIENT_TLS))
            assertTrue(X509KeyStore.fromFile(it, keyStorePassword).internal.isKeyEntry(CORDA_CLIENT_TLS))
        }

        listOf("artemisbridge.jks").map { workingDirectory / "artemis" / it }.forEach {
            assertTrue(it.exists())
            assertTrue(X509KeyStore.fromFile(it, keyStorePassword).contains(CORDA_CLIENT_TLS))
            assertTrue(X509KeyStore.fromFile(it, keyStorePassword).internal.isKeyEntry(CORDA_CLIENT_TLS))
        }

        checkKeystore(workingDirectory / "artemis/artemisbridge.jks", "${CORDA_CLIENT_TLS}", keyStorePassword,
                keyStorePassword)

        X509KeyStore.fromFile(workingDirectory / "artemis" / "artemis-root.jks", keyStorePassword).update {
            assertTrue(contains(CORDA_ROOT_CA))
            assertTrue(internal.isKeyEntry(CORDA_ROOT_CA))
        }

        X509KeyStore.fromFile(workingDirectory / "artemis" / "artemis-truststore.jks", trustStorePassword).update {
            assertTrue(contains(CORDA_ROOT_CA))
            assertFalse(internal.isKeyEntry(CORDA_ROOT_CA))
        }
    }
}

private class MockedCryptoService(val keystorePath: Path) : CryptoService {

    private val bouncyCryptoService: BCCryptoService

    init {
        System.out.println("keystorePath used by mock crypto service == " + keystorePath)
        val certificateStoreSupplier = FileBasedCertificateStoreSupplier(keystorePath / "keys.jks", "password", "password")
        val principal = CordaX500Name("aa","bb","cc","GB").x500Principal
        bouncyCryptoService = BCCryptoService(principal, certificateStoreSupplier)
    }

    override fun containsKey(alias: String): Boolean {
        return bouncyCryptoService.containsKey(alias)
    }

    override fun getPublicKey(alias: String): PublicKey? {
        return bouncyCryptoService.getPublicKey(alias)
    }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
        return bouncyCryptoService.sign(alias, data, signAlgorithm)
    }

    override fun getSigner(alias: String): ContentSigner {
        return bouncyCryptoService.getSigner(alias)
    }

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
        return bouncyCryptoService.generateKeyPair(alias, scheme)
    }
}

