package net.corda.bootstrapper

import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.node.JavaPackageName
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.JarSignatureTestUtils.generateKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import picocli.CommandLine
import java.nio.file.Files

class PackageOwnerParsingTest {

    @Rule
    @JvmField
    val expectedEx: ExpectedException = ExpectedException.none()

    companion object {

        private const val ALICE = "alice"
        private const val ALICE_PASS = "alicepass"
        private const val BOB = "bob"
        private const val BOB_PASS = "bobpass"
        private const val CHARLIE = "charlie"
        private const val CHARLIE_PASS = "charliepass"

        private val dirAlice = Files.createTempDirectory(ALICE)
        private val dirBob = Files.createTempDirectory(BOB)
        private val dirCharlie = Files.createTempDirectory(CHARLIE)

        val packageOwner = PackageOwner()
        val commandLine = CommandLine(packageOwner)

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            println(dirAlice)
            dirAlice.generateKey(ALICE, ALICE_PASS, ALICE_NAME.toString())
            println(dirAlice.list())
            dirBob.generateKey(BOB, BOB_PASS, BOB_NAME.toString(), "EC")
            dirCharlie.generateKey(CHARLIE, CHARLIE_PASS, CHARLIE_NAME.toString(), "DSA")
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            dirAlice.deleteRecursively()
        }
    }

    @Test
    fun `parse registration request with single mapping`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff=$aliceKeyStorePath:$ALICE_PASS:$ALICE")
        commandLine.parse(*args)
        println(packageOwner)
        assertThat(packageOwner.registerPackageOwnership).containsKey(JavaPackageName("com.example.stuff"))
    }

    @Test
    fun `parse registration request with invalid arguments`() {
        val args = arrayOf("--register-package-owner", "com.!example.stuff")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("should be in KEY=VALUE format")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with incorrect keystore specification`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff=$aliceKeyStorePath:$ALICE_PASS")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("keystore argument must specify 3 elements")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with invalid java package name`() {
        val args = arrayOf("--register-package-owner", "com.!example.stuff=A:B:C")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("Invalid Java package name")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with invalid keystore file`() {
        val args = arrayOf("--register-package-owner", "com.example.stuff=NONSENSE:B:C")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("java.nio.file.NoSuchFileException")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with invalid keystore password`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff=$aliceKeyStorePath:BAD_PASSWORD:$ALICE")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("Keystore was tampered with, or password was incorrect")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with invalid keystore alias`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff=$aliceKeyStorePath:$ALICE_PASS:BAD_ALIAS")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("must not be null")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with multiple arguments`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val bobKeyStorePath = dirBob / "_teststore"
        val charlieKeyStorePath = dirCharlie / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff=$aliceKeyStorePath:$ALICE_PASS:$ALICE",
                                        "--register-package-owner", "com.example.more.stuff=$bobKeyStorePath:$BOB_PASS:$BOB",
                                        "--register-package-owner", "com.example.even.more.stuff=$charlieKeyStorePath:$CHARLIE_PASS:$CHARLIE")
        commandLine.parse(*args)
        println(packageOwner)
        assertThat(packageOwner.registerPackageOwnership).hasSize(3)
    }

    @Test
    fun `parse unregister request with single mapping`() {
        val args = arrayOf("--unregister-package-owner", "com.example.stuff")
        commandLine.parse(*args)
        assertThat(packageOwner.unregisterPackageOwnership).contains(JavaPackageName("com.example.stuff"))
    }

    @Test
    fun `parse mixed register and unregister request`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff=$aliceKeyStorePath:$ALICE_PASS:$ALICE",
                                        "--unregister-package-owner", "com.example.stuff2")
        commandLine.parse(*args)
        assertThat(packageOwner.registerPackageOwnership).containsKey(JavaPackageName("com.example.stuff"))
        assertThat(packageOwner.unregisterPackageOwnership).contains(JavaPackageName("com.example.stuff2"))
    }
}

