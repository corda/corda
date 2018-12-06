package net.corda.bootstrapper

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import net.corda.core.internal.copyTo
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.network.CopyCordapps
import net.corda.nodeapi.internal.network.NetworkBootstrapperWithOverridableParameters
import net.corda.nodeapi.internal.network.NetworkParametersOverrides
import net.corda.nodeapi.internal.network.PackageOwner
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.getPublicKey
import org.junit.*
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NetworkBootstrapperRunnerTests {
    private val outContent = ByteArrayOutputStream()
    private val errContent = ByteArrayOutputStream()
    private val originalOut = System.out
    private val originalErr = System.err

    @Before
    fun setUpStreams() {
        System.setOut(PrintStream(outContent))
        System.setErr(PrintStream(errContent))
    }

    @After
    fun restoreStreams() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    companion object {
        private const val ALICE = "alice"
        private const val ALICE_PASS = "alicepass"

        private const val aliceConfigFile = "alice-network.conf"
        private const val correctNetworkFile = "correct-network.conf"
        private const val packageOverlapConfigFile = "package-overlap.conf"

        private val dirAlice = Files.createTempDirectory(ALICE)
        private val dirAliceEC = Files.createTempDirectory("sdfsdfds")
        private val dirAliceDSA = Files.createTempDirectory(ALICE)

        private lateinit var alicePublicKey: PublicKey
        private lateinit var alicePublicKeyEC: PublicKey
        private lateinit var alicePublicKeyDSA: PublicKey

        private val resourceDirectory = Paths.get(".") / "src" / "test" / "resources"

        private fun String.copyToTestDir(dir: Path = dirAlice): Path {
            return (resourceDirectory / this).copyTo(dir / this)
        }

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            dirAlice.generateKey(ALICE, ALICE_PASS, ALICE_NAME.toString())
            dirAliceEC.generateKey(ALICE, ALICE_PASS, ALICE_NAME.toString(), "EC")
            dirAliceDSA.generateKey(ALICE, ALICE_PASS, ALICE_NAME.toString(), "DSA")
            alicePublicKey = dirAlice.getPublicKey(ALICE, ALICE_PASS)
            alicePublicKeyEC = dirAliceEC.getPublicKey(ALICE, ALICE_PASS)
            alicePublicKeyDSA = dirAliceDSA.getPublicKey(ALICE, ALICE_PASS)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            dirAlice.deleteRecursively()
        }
    }

    private fun getRunner(): Pair<NetworkBootstrapperRunner, NetworkBootstrapperWithOverridableParameters> {
        val mockBootstrapper = mock<NetworkBootstrapperWithOverridableParameters>()
        return Pair(NetworkBootstrapperRunner(mockBootstrapper), mockBootstrapper)
    }

    @Test
    fun `test when defaults are run bootstrapper is called correctly`() {
        val (runner, mockBootstrapper) = getRunner()
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides())
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when base directory is specified it is passed through to the bootstrapper`() {
        val (runner, mockBootstrapper) = getRunner()
        val tempDir = createTempDir()
        runner.dir = tempDir.toPath()
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(tempDir.toPath().toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides())
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when no copy flag is specified it is passed through to the bootstrapper`() {
        val (runner, mockBootstrapper) = getRunner()
        runner.noCopy = true
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.No, NetworkParametersOverrides())
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when copy cordapps is specified it is passed through to the bootstrapper`() {
        val (runner, mockBootstrapper) = getRunner()
        runner.copyCordapps = CopyCordapps.No
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.No, NetworkParametersOverrides())
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when min platform version is specified it is passed through to the bootstrapper`() {
        val (runner, mockBootstrapper) = getRunner()
        runner.minimumPlatformVersion = 1
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides(minimumPlatformVersion = 1))
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when min platform version is invalid it fails to run with a sensible error message`() {
        val runner = getRunner().first
        runner.minimumPlatformVersion = 0
        val exception = assertFailsWith<IllegalArgumentException> { runner.runProgram() }
        assertEquals("The --minimum-platform-version parameter must be at least 1", exception.message)
    }

    @Test
    fun `test when max message size is specified it is passed through to the bootstrapper`() {
        val (runner, mockBootstrapper) = getRunner()
        runner.maxMessageSize = 1
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides(maxMessageSize = 1))
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when max message size is invalid it fails to run with a sensible error message`() {
        val runner = getRunner().first
        runner.maxMessageSize = 0
        val exception = assertFailsWith<IllegalArgumentException> { runner.runProgram() }
        assertEquals("The --max-message-size parameter must be at least 1", exception.message)
    }

    @Test
    fun `test when max transaction size is specified it is passed through to the bootstrapper`() {
        val (runner, mockBootstrapper) = getRunner()
        runner.maxTransactionSize = 1
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides(maxTransactionSize = 1))
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when max transaction size is invalid it fails to run with a sensible error message`() {
        val runner = getRunner().first
        runner.maxTransactionSize = 0
        val exception = assertFailsWith<IllegalArgumentException> { runner.runProgram() }
        assertEquals("The --max-transaction-size parameter must be at least 1", exception.message)
    }

    @Test
    fun `test when event horizon is specified it is passed through to the bootstrapper`() {
        val (runner, mockBootstrapper) = getRunner()
        runner.eventHorizon = 7.days
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides(eventHorizon = 7.days))
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when event horizon is invalid it fails to run with a sensible error message`() {
        val runner = getRunner().first
        runner.eventHorizon = (-7).days
        val exception = assertFailsWith<IllegalArgumentException> { runner.runProgram() }
        assertEquals("The --event-horizon parameter must be a positive value", exception.message)
    }

    @Test
    fun `test when a network parameters is specified the values are passed through to the bootstrapper`() {
        val (runner, mockBootstrapper) = getRunner()
        val conf = correctNetworkFile.copyToTestDir()
        runner.networkParametersFile = conf
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides(
                maxMessageSize = 10000,
                maxTransactionSize = 2000,
                eventHorizon = 5.days,
                minimumPlatformVersion = 2
        ))
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when a package is specified in the network parameters file it is passed through to the bootstrapper`() {
        val (runner, mockBootstrapper) = getRunner()
        val conf = aliceConfigFile.copyToTestDir()
        runner.networkParametersFile = conf
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides(
                packageOwnership = listOf(PackageOwner("com.example.stuff", publicKey = alicePublicKey))
        ))
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when a package is specified in the network parameters file it is passed through to the bootstrapper EC`() {
        val (runner, mockBootstrapper) = getRunner()
        val conf = aliceConfigFile.copyToTestDir(dirAliceEC)
        runner.networkParametersFile = conf
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides(
                packageOwnership = listOf(PackageOwner("com.example.stuff", publicKey = alicePublicKeyEC))
        ))
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when a package is specified in the network parameters file it is passed through to the bootstrapper DSA`() {
        val (runner, mockBootstrapper) = getRunner()
        val conf = aliceConfigFile.copyToTestDir(dirAliceDSA)
        runner.networkParametersFile = conf
        val exitCode = runner.runProgram()
        verify(mockBootstrapper).bootstrap(Paths.get(".").toAbsolutePath().normalize(), CopyCordapps.FirstRunOnly, NetworkParametersOverrides(
                packageOwnership = listOf(PackageOwner("com.example.stuff", publicKey = alicePublicKeyDSA))
        ))
        assertEquals(0, exitCode)
    }

    @Test
    fun `test when packages overlap that the bootstrapper fails with a sensible message`() {
        val (runner, mockBootstrapper) = getRunner()
        val conf = packageOverlapConfigFile.copyToTestDir()
        runner.networkParametersFile = conf
        val exitCode = runner.runProgram()
        val output = errContent.toString()
        assert(output.contains("Error parsing packageOwnership: Package namespaces must not overlap"))
        assertEquals(1, exitCode)
    }

    @Test
    fun `test when keyfile does not exist then bootstrapper fails with a sensible message`() {
        val (runner, mockBootstrapper) = getRunner()
        runner.networkParametersFile = dirAlice / "filename-that-doesnt-exist"
        val exception = assertFailsWith<FileNotFoundException> { runner.runProgram() }
        assert(exception.message!!.startsWith("Unable to find specified network parameters config file at"))
    }
}