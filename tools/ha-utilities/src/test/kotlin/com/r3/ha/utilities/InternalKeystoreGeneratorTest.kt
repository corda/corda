package com.r3.ha.utilities

import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InternalKeystoreGeneratorTest {
    private val generator = InternalKeystoreGenerator()

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `generate keystores correctly`() {
        val workingDirectory = tempFolder.root.toPath()
        CommandLine.populateCommand(generator, BASE_DIR, workingDirectory.toString())
        Assertions.assertThat(generator.baseDirectory).isEqualTo(workingDirectory)
        generator.runProgram()

        listOf("float.jks").map { workingDirectory / "tunnel" / it }.forEach {
            assertTrue(it.exists())
            assertTrue(X509KeyStore.fromFile(it, generator.password).contains(X509Utilities.CORDA_CLIENT_TLS))
            assertTrue(X509KeyStore.fromFile(it, generator.password).internal.isKeyEntry(X509Utilities.CORDA_CLIENT_TLS))
        }

        X509KeyStore.fromFile(workingDirectory / "tunnel" / "tunnel-root.jks", generator.password).update {
            assertTrue(contains(X509Utilities.CORDA_ROOT_CA))
            assertTrue(internal.isKeyEntry(X509Utilities.CORDA_ROOT_CA))
        }

        X509KeyStore.fromFile(workingDirectory / "tunnel" / "tunnel-truststore.jks", generator.password).update {
            assertTrue(contains(X509Utilities.CORDA_ROOT_CA))
            assertFalse(internal.isKeyEntry(X509Utilities.CORDA_ROOT_CA))
        }

        listOf("bridge.jks", "artemis.jks", "artemis-client.jks").map { workingDirectory / "artemis" / it }.forEach {
            assertTrue(it.exists())
            assertTrue(X509KeyStore.fromFile(it, generator.password).contains(X509Utilities.CORDA_CLIENT_TLS))
            assertTrue(X509KeyStore.fromFile(it, generator.password).internal.isKeyEntry(X509Utilities.CORDA_CLIENT_TLS))
        }

        X509KeyStore.fromFile(workingDirectory / "artemis" / "artemis-root.jks", generator.password).update {
            assertTrue(contains(X509Utilities.CORDA_ROOT_CA))
            assertTrue(internal.isKeyEntry(X509Utilities.CORDA_ROOT_CA))
        }

        X509KeyStore.fromFile(workingDirectory / "artemis" / "artemis-truststore.jks", generator.password).update {
            assertTrue(contains(X509Utilities.CORDA_ROOT_CA))
            assertFalse(internal.isKeyEntry(X509Utilities.CORDA_ROOT_CA))
        }
    }
}