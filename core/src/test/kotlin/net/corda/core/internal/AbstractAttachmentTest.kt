package net.corda.core.internal

import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AbstractAttachmentTest {
    companion object {
        private val dir = Files.createTempDirectory(AbstractAttachmentTest::class.simpleName)
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

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            execute("keytool", "-genkey", "-keystore", "_teststore", "-storepass", "storepass", "-keyalg", "RSA", "-alias", "alice", "-keypass", "alicepass", "-dname", ALICE_NAME.toString())
            execute("keytool", "-genkey", "-keystore", "_teststore", "-storepass", "storepass", "-keyalg", "RSA", "-alias", "bob", "-keypass", "bobpass", "-dname", BOB_NAME.toString())
            (dir / "_signable1").writeLines(listOf("signable1"))
            (dir / "_signable2").writeLines(listOf("signable2"))
            (dir / "_signable3").writeLines(listOf("signable3"))
        }

        private fun load(name: String) = object : AbstractAttachment((dir / name)::readAll) {
            override val id get() = throw UnsupportedOperationException()
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            dir.deleteRecursively()
        }
    }

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
        execute("jar", "cvf", "attachment.jar", "META-INF")
        assertEquals(emptyList(), load("attachment.jar").signers)
        execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", "alicepass", "attachment.jar", "alice")
        assertEquals(emptyList(), load("attachment.jar").signers) // There needs to have been a file for ALICE to sign.
    }

    @Test
    fun `unsigned jar has no signers`() {
        execute("jar", "cvf", "attachment.jar", "_signable1")
        assertEquals(emptyList(), load("attachment.jar").signers)
        execute("jar", "uvf", "attachment.jar", "_signable2")
        assertEquals(emptyList(), load("attachment.jar").signers)
    }

    @Test
    fun `one signer`() {
        execute("jar", "cvf", "attachment.jar", "_signable1", "_signable2")
        execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", "alicepass", "attachment.jar", "alice")
        assertEquals(listOf(ALICE_NAME), load("attachment.jar").signers.map { it.name }) // We only reused ALICE's distinguished name, so the keys will be different.
        (dir / "my-dir").createDirectory()
        execute("jar", "uvf", "attachment.jar", "my-dir")
        assertEquals(listOf(ALICE_NAME), load("attachment.jar").signers.map { it.name }) // Unsigned directory is irrelevant.
    }

    @Test
    fun `two signers`() {
        execute("jar", "cvf", "attachment.jar", "_signable1", "_signable2")
        execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", "alicepass", "attachment.jar", "alice")
        execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", "bobpass", "attachment.jar", "bob")
        assertEquals(listOf(ALICE_NAME, BOB_NAME), load("attachment.jar").signers.map { it.name })
    }

    @Test
    fun `all files must be signed by the same set of signers`() {
        execute("jar", "cvf", "attachment.jar", "_signable1")
        execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", "alicepass", "attachment.jar", "alice")
        assertEquals(listOf(ALICE_NAME), load("attachment.jar").signers.map { it.name })
        execute("jar", "uvf", "attachment.jar", "_signable2")
        execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", "bobpass", "attachment.jar", "bob")
        assertFailsWith<InvalidJarSignersException>(
            """
            Mismatch between signers [O=Alice Corp, L=Madrid, C=ES, O=Bob Plc, L=Rome, C=IT] for file _signable1
            and signers [O=Bob Plc, L=Rome, C=IT] for file _signable2
            """.trimIndent().replace('\n', ' ')
        ) { load("attachment.jar").signers }
    }

    @Test
    fun `bad signature is caught even if the party would not qualify as a signer`() {
        (dir / "volatile").writeLines(listOf("volatile"))
        execute("jar", "cvf", "attachment.jar", "volatile")
        execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", "alicepass", "attachment.jar", "alice")
        assertEquals(listOf(ALICE_NAME), load("attachment.jar").signers.map { it.name })
        (dir / "volatile").writeLines(listOf("garbage"))
        execute("jar", "uvf", "attachment.jar", "volatile", "_signable1") // ALICE's signature on volatile is now bad.
        execute("jarsigner", "-keystore", "_teststore", "-storepass", "storepass", "-keypass", "bobpass", "attachment.jar", "bob")
        val a = load("attachment.jar")
        // The JDK doesn't care that BOB has correctly signed the whole thing, it won't let us process the entry with ALICE's bad signature:
        assertThatThrownBy { a.signers }.isInstanceOf(SecurityException::class.java)
    }
}
