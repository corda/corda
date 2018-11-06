package net.corda.bootstrapper

import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.node.JavaPackageName
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.JarSignatureTestUtils.generateKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
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

        val networkBootstrapper = NetworkBootstrapperRunner()
        val commandLine = CommandLine(networkBootstrapper)

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            dirAlice.generateKey(ALICE, ALICE_PASS, ALICE_NAME.toString())
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
        val args = arrayOf("--register-package-owner", "com.example.stuff;$aliceKeyStorePath;$ALICE_PASS;$ALICE")
        commandLine.parse(*args)
        assertThat(networkBootstrapper.registerPackageOwnership[0].javaPackageName).isEqualTo(JavaPackageName("com.example.stuff"))
    }

    @Test
    fun `parse registration request with invalid arguments`() {
        val args = arrayOf("--register-package-owner", "com.!example.stuff")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("Package owner must specify 4 elements separated by semi-colon")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with incorrect keystore specification`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff;$aliceKeyStorePath$ALICE_PASS")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("Package owner must specify 4 elements separated by semi-colon")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with invalid java package name`() {
        val args = arrayOf("--register-package-owner", "com.!example.stuff;A;B;C")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("Invalid Java package name")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with invalid keystore file`() {
        val args = arrayOf("--register-package-owner", "com.example.stuff;NONSENSE;B;C")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("Error reading the key store from the file")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with invalid keystore password`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff;$aliceKeyStorePath;BAD_PASSWORD;$ALICE")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("Error reading the key store from the file")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with invalid keystore alias`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff;$aliceKeyStorePath;$ALICE_PASS;BAD_ALIAS")
        expectedEx.expect(CommandLine.ParameterException::class.java)
        expectedEx.expectMessage("must not be null")
        commandLine.parse(*args)
    }

    @Test
    fun `parse registration request with multiple arguments`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val bobKeyStorePath = dirBob / "_teststore"
        val charlieKeyStorePath = dirCharlie / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff;$aliceKeyStorePath;$ALICE_PASS;$ALICE",
                                        "--register-package-owner", "com.example.more.stuff;$bobKeyStorePath;$BOB_PASS;$BOB",
                                        "--register-package-owner", "com.example.even.more.stuff;$charlieKeyStorePath;$CHARLIE_PASS;$CHARLIE")
        commandLine.parse(*args)
        assertThat(networkBootstrapper.registerPackageOwnership).hasSize(3)
    }

    @Ignore("Ignoring this test as the delimiters don't work correctly, see CORDA-2191")
    @Test
    fun `parse registration request with delimiter inclusive passwords`() {
        val aliceKeyStorePath1 = dirAlice / "_alicestore1"
        dirAlice.generateKey("${ALICE}1", "passw;rd", ALICE_NAME.toString(), storeName = "_alicestore1")
        val aliceKeyStorePath2 = dirAlice / "_alicestore2"
        dirAlice.generateKey("${ALICE}2", "\"passw;rd\"", ALICE_NAME.toString(), storeName = "_alicestore2")
        val aliceKeyStorePath3 = dirAlice / "_alicestore3"
        dirAlice.generateKey("${ALICE}3", "passw;rd", ALICE_NAME.toString(), storeName = "_alicestore3")
        val aliceKeyStorePath4 = dirAlice / "_alicestore4"
        dirAlice.generateKey("${ALICE}4", "\'passw;rd\'", ALICE_NAME.toString(), storeName = "_alicestore4")
        val aliceKeyStorePath5 = dirAlice / "_alicestore5"
        dirAlice.generateKey("${ALICE}5", "\"\"passw;rd\"\"", ALICE_NAME.toString(), storeName = "_alicestore5")
        val packageOwnerSpecs = listOf("net.something0;$aliceKeyStorePath1;passw;rd;${ALICE}1",
                "net.something1;$aliceKeyStorePath2;\"passw;rd\";${ALICE}2",
                "\"net.something2;$aliceKeyStorePath3;passw;rd;${ALICE}3\"",
                "net.something3;$aliceKeyStorePath4;\'passw;rd\';${ALICE}4",
                "net.something4;$aliceKeyStorePath5;\"\"passw;rd\"\";${ALICE}5")
        packageOwnerSpecs.forEachIndexed { i, packageOwnerSpec ->
            commandLine.parse(*arrayOf("--register-package-owner", packageOwnerSpec))
            assertThat(networkBootstrapper.registerPackageOwnership[0].javaPackageName).isEqualTo(JavaPackageName("net.something$i"))
        }
    }

    @Test
    fun `parse unregister request with single mapping`() {
        val args = arrayOf("--unregister-package-owner", "com.example.stuff")
        commandLine.parse(*args)
        assertThat(networkBootstrapper.unregisterPackageOwnership).contains(JavaPackageName("com.example.stuff"))
    }

    @Test
    fun `parse mixed register and unregister request`() {
        val aliceKeyStorePath = dirAlice / "_teststore"
        val args = arrayOf("--register-package-owner", "com.example.stuff;$aliceKeyStorePath;$ALICE_PASS;$ALICE",
                                        "--unregister-package-owner", "com.example.stuff2")
        commandLine.parse(*args)
        assertThat(networkBootstrapper.registerPackageOwnership.map { it.javaPackageName }).contains(JavaPackageName("com.example.stuff"))
        assertThat(networkBootstrapper.unregisterPackageOwnership).contains(JavaPackageName("com.example.stuff2"))
    }
}

