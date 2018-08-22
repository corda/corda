package net.corda.core.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
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

    private val List<Party>.names get() = map { it.name }

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
        signAsAlice()
        assertEquals(listOf(ALICE_NAME), getJarSigners().names) // We only reused ALICE's distinguished name, so the keys will be different.

        (dir / "my-dir").createDirectory()
        updateJar("my-dir")
        assertEquals(listOf(ALICE_NAME), getJarSigners().names) // Unsigned directory is irrelevant.
    }

    @Test
    fun `two signers`() {
        createJar("_signable1", "_signable2")
        signAsAlice()
        signAsBob()

        assertEquals(listOf(ALICE_NAME, BOB_NAME), getJarSigners().names)
    }

    @Test
    fun `all files must be signed by the same set of signers`() {
        createJar("_signable1")
        signAsAlice()
        assertEquals(listOf(ALICE_NAME), getJarSigners().names)

        updateJar("_signable2")
        signAsBob()
        assertFailsWith<InvalidJarSignersException>(
            """
            Mismatch between signers [O=Alice Corp, L=Madrid, C=ES, O=Bob Plc, L=Rome, C=IT] for file _signable1
            and signers [O=Bob Plc, L=Rome, C=IT] for file _signable2
            """.trimIndent().replace('\n', ' ')
        ) { getJarSigners() }
    }

    @Test
    fun `bad signature is caught even if the party would not qualify as a signer`() {
        (dir / "volatile").writeLines(listOf("volatile"))
        createJar("volatile")
        signAsAlice()
        assertEquals(listOf(ALICE_NAME), getJarSigners().names)

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

    private fun signJar(alias: String, password: String) =
            execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", password, FILENAME, alias)

    private fun signAsAlice() = signJar(ALICE, ALICE_PASS)
    private fun signAsBob() = signJar(BOB, BOB_PASS)

    private fun getJarSigners() =
            JarInputStream(FileInputStream((dir / FILENAME).toFile())).use(JarSignatureCollector::collectSigningParties)
    //endregion
    
}
