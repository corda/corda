package net.corda.testing.core

import net.corda.testing.core.internal.JarSignatureTestUtils.createJar
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.getJarSigners
import net.corda.testing.core.internal.JarSignatureTestUtils.signJar
import net.corda.testing.core.internal.JarSignatureTestUtils.updateJar
import net.corda.testing.core.internal.JarSignatureTestUtils.addIndexList
import net.corda.core.identity.Party
import net.corda.core.internal.*
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JarSignatureCollectorTest {
    companion object {
        private val dir = Files.createTempDirectory(JarSignatureCollectorTest::class.simpleName)

        private const val FILENAME = "attachment.jar"
        private const val ALICE = "alice"
        private const val ALICE_PASS = "alicepass"
        private const val BOB = "bob"
        private const val BOB_PASS = "bobpass"
        private const val CHARLIE = "Charlie"
        private const val CHARLIE_PASS = "charliepass"

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            dir.generateKey(ALICE, "storepass", ALICE_NAME.toString(), keyPassword = ALICE_PASS)
            dir.generateKey(BOB, "storepass", BOB_NAME.toString(), keyPassword = BOB_PASS)
            dir.generateKey(CHARLIE, "storepass", CHARLIE_NAME.toString(), "EC", CHARLIE_PASS)

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

    @Test(timeout=300_000)
	fun `empty jar has no signers`() {
        (dir / "META-INF").createDirectory() // At least one arg is required, and jar cvf conveniently ignores this.
        dir.createJar(FILENAME, "META-INF")
        assertEquals(emptyList(), dir.getJarSigners(FILENAME))

        signAsAlice()
        assertEquals(emptyList(), dir.getJarSigners(FILENAME)) // There needs to have been a file for ALICE to sign.
    }

    @Test(timeout=300_000)
	fun `unsigned jar has no signers`() {
        dir.createJar(FILENAME, "_signable1")
        assertEquals(emptyList(), dir.getJarSigners(FILENAME))

        dir.updateJar(FILENAME, "_signable2")
        assertEquals(emptyList(), dir.getJarSigners(FILENAME))
    }

    @Test(timeout=300_000)
	fun `one signer`() {
        dir.createJar(FILENAME, "_signable1", "_signable2")
        val key = signAsAlice()
        assertEquals(listOf(key), dir.getJarSigners(FILENAME))

        (dir / "my-dir").createDirectory()
        dir.updateJar(FILENAME, "my-dir")
        assertEquals(listOf(key), dir.getJarSigners(FILENAME)) // Unsigned directory is irrelevant.
    }

    @Test(timeout=300_000)
	fun `two signers`() {
        dir.createJar(FILENAME, "_signable1", "_signable2")
        val key1 = signAsAlice()
        val key2 = signAsBob()

        assertEquals(setOf(key1, key2), dir.getJarSigners(FILENAME).toSet())
    }

    @Test(timeout=300_000)
	fun `all files must be signed by the same set of signers`() {
        dir.createJar(FILENAME, "_signable1")
        val key1 = signAsAlice()
        assertEquals(listOf(key1), dir.getJarSigners(FILENAME))

        dir.updateJar(FILENAME, "_signable2")
        signAsBob()
        assertFailsWith<InvalidJarSignersException>(
                """
            Mismatch between signers [O=Alice Corp, L=Madrid, C=ES, O=Bob Plc, L=Rome, C=IT] for file _signable1
            and signers [O=Bob Plc, L=Rome, C=IT] for file _signable2.
            """.trimIndent().replace('\n', ' ')
        ) { dir.getJarSigners(FILENAME) }
    }

    @Test(timeout=300_000)
	fun `bad signature is caught even if the party would not qualify as a signer`() {
        (dir / "volatile").writeLines(listOf("volatile"))
        dir.createJar(FILENAME, "volatile")
        val key1 = signAsAlice()
        assertEquals(listOf(key1), dir.getJarSigners(FILENAME))

        (dir / "volatile").writeLines(listOf("garbage"))
        dir.updateJar(FILENAME, "volatile", "_signable1") // ALICE's signature on volatile is now bad.
        signAsBob()
        // The JDK doesn't care that BOB has correctly signed the whole thing, it won't let us process the entry with ALICE's bad signature:
        assertFailsWith<SecurityException> { dir.getJarSigners(FILENAME) }
    }

    // Signing with EC algorithm produces META-INF/*.EC file name not compatible with JAR File Spec however it's compatible with java.util.JarVerifier
    // and our JarSignatureCollector
    @Test(timeout=300_000)
	fun `one signer with EC algorithm`() {
        dir.createJar(FILENAME, "_signable1", "_signable2")
        // JDK11: Warning:  Different store and key passwords not supported for PKCS12 KeyStores. Ignoring user-specified -keypass value.
        val key = signAs(CHARLIE, CHARLIE_PASS)
        assertEquals(listOf(key), dir.getJarSigners(FILENAME)) // We only used CHARLIE's distinguished name, so the keys will be different.
    }

    @Test(timeout=300_000)
	fun `jar with jar index file`() {
        dir.createJar(FILENAME, "_signable1")
        dir.addIndexList(FILENAME)
        val key = signAsAlice()
        assertEquals(listOf(key), dir.getJarSigners(FILENAME))
    }

    private fun signAsAlice() = signAs(ALICE, ALICE_PASS)
    private fun signAsBob() = signAs(BOB, BOB_PASS)

    // JDK11: Warning:  Different store and key passwords not supported for PKCS12 KeyStores. Ignoring user-specified -keypass value.
    // TODO: use programmatic API support to implement signing (see https://docs.oracle.com/javase/9/docs/api/jdk/security/jarsigner/JarSigner.html)
    private fun signAs(alias: String, keyPassword: String = alias) : PublicKey {
        return if (SystemUtils.IS_JAVA_11)
            dir.signJar(FILENAME, alias, "storepass", "storepass")
        else
            dir.signJar(FILENAME, alias, "storepass", keyPassword)
    }
}
