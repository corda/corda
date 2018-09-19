package net.corda.core.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.nodeapi.internal.crypto.loadKeyStore
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.util.jar.JarInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JarSignatureCollectorTest {
    companion object {
        private val dir = Files.createTempDirectory(JarSignatureCollectorTest::class.simpleName)
        private val bin = Paths.get(System.getProperty("java.home")).let { if (it.endsWith("jre")) it.parent else it } / "bin"
        private val shredder = (dir / "_shredder").toFile() // No need to delete after each test.

        fun execute(vararg command: String) {
            assertEquals(0, ProcessBuilder()
                    .inheritIO()
                    .redirectOutput(shredder)
                    .directory(dir.toFile())
                    .command((bin / command[0]).toString(), *command.sliceArray(1 until command.size))
                    .start()
                    .waitFor())
        }

        private const val FILENAME = "attachment.jar"
        private const val ALICE = "alice"
        private const val ALICE_PASS = "alicepass"
        private const val BOB = "bob"
        private const val BOB_PASS = "bobpass"

        private fun generateKey(alias: String, password: String, name: CordaX500Name) =
                execute("keytool", "-genkey", "-keystore", "_teststore", "-storepass", "storepass", "-keyalg", "RSA", "-alias", alias, "-keypass", password, "-dname", name.toString())

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            generateKey(ALICE, ALICE_PASS, ALICE_NAME)
            generateKey(BOB, BOB_PASS, BOB_NAME)

            (dir / "_signable1").writeLines(listOf("signable1"))
            (dir / "_signable2").writeLines(listOf("signable2"))
            (dir / "_signable3").writeLines(listOf("signable3"))
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            dir.deleteRecursively()
        }
    }

    private val List<Party>.keys get() = map { it.owningKey }

    @After
    fun tearDown() {
        dir.list {
            it.filter { !it.fileName.toString().startsWith("_") }.forEach(Path::deleteRecursively)
        }
        assertThat(dir.list()).hasSize(5)
    }

    @Test
    fun `empty jar has no signers`() {
        (dir / "META-INF").createDirectory() // At least one arg is required, and jar cvf conveniently ignores this.
        createJar("META-INF")
        assertEquals(emptyList(), getJarSigners())

        signAsAlice()
        assertEquals(emptyList(), getJarSigners()) // There needs to have been a file for ALICE to sign.
    }

    @Test
    fun `unsigned jar has no signers`() {
        createJar("_signable1")
        assertEquals(emptyList(), getJarSigners())

        updateJar("_signable2")
        assertEquals(emptyList(), getJarSigners())
    }

    @Test
    fun `one signer`() {
        createJar("_signable1", "_signable2")
        val key = signAsAlice()
        assertEquals(listOf(key), getJarSigners())

        (dir / "my-dir").createDirectory()
        updateJar("my-dir")
        assertEquals(listOf(key), getJarSigners()) // Unsigned directory is irrelevant.
    }

    @Test
    fun `two signers`() {
        createJar("_signable1", "_signable2")
        val key1 = signAsAlice()
        val key2 = signAsBob()

        assertEquals(setOf(key1, key2), getJarSigners().toSet())
    }

    @Test
    fun `all files must be signed by the same set of signers`() {
        createJar("_signable1")
        val key1 = signAsAlice()
        assertEquals(listOf(key1), getJarSigners())

        updateJar("_signable2")
        signAsBob()
        assertFailsWith<InvalidJarSignersException>(
                """
            Mismatch between signers [O=Alice Corp, L=Madrid, C=ES, O=Bob Plc, L=Rome, C=IT] for file _signable1
            and signers [O=Bob Plc, L=Rome, C=IT] for file _signable2.
            See https://docs.corda.net/design/data-model-upgrades/signature-constraints.html for details of the
            constraints applied to attachment signatures.
            """.trimIndent().replace('\n', ' ')
        ) { getJarSigners() }
    }

    @Test
    fun `bad signature is caught even if the party would not qualify as a signer`() {
        (dir / "volatile").writeLines(listOf("volatile"))
        createJar("volatile")
        val key1 = signAsAlice()
        assertEquals(listOf(key1), getJarSigners())

        (dir / "volatile").writeLines(listOf("garbage"))
        updateJar("volatile", "_signable1") // ALICE's signature on volatile is now bad.
        signAsBob()
        // The JDK doesn't care that BOB has correctly signed the whole thing, it won't let us process the entry with ALICE's bad signature:
        assertFailsWith<SecurityException> { getJarSigners() }
    }

    //region Helper functions
    private fun createJar(vararg contents: String) =
            execute(*(arrayOf("jar", "cvf", FILENAME) + contents))

    private fun updateJar(vararg contents: String) =
            execute(*(arrayOf("jar", "uvf", FILENAME) + contents))

    private fun signJar(alias: String, password: String): PublicKey {
        execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", password, FILENAME, alias)
        val ks = loadKeyStore(dir.resolve("_teststore"), "storepass")
        return ks.getCertificate(alias).publicKey
    }

    private fun signAsAlice() = signJar(ALICE, ALICE_PASS)
    private fun signAsBob() = signJar(BOB, BOB_PASS)

    private fun getJarSigners() =
            JarInputStream(FileInputStream((dir / FILENAME).toFile())).use(JarSignatureCollector::collectSigners)
    //endregion

}
