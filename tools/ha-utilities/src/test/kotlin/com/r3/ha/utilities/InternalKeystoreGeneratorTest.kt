package com.r3.ha.utilities

import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.crypto.getCertificateAndKeyPair
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import java.nio.file.Path
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
}