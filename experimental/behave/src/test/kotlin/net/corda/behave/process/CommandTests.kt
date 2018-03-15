package net.corda.behave.process

import net.corda.behave.file.currentDirectory
import net.corda.behave.file.div
import net.corda.behave.file.doormanConfigDirectory
import net.corda.behave.node.Distribution
import net.corda.core.CordaRuntimeException
import net.corda.core.utilities.minutes
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.*
import org.junit.Test
import rx.observers.TestSubscriber
import java.nio.file.Files
import java.nio.file.Paths

class CommandTests {

    @Test
    fun `successful command returns zero`() {

        val all = doormanConfigDirectory.listFiles()
        val nodeInfoFile = doormanConfigDirectory.listFiles { _, filename -> filename.matches("nodeInfo-*".toRegex()) }.firstOrNull() ?: throw CordaRuntimeException("Missing notary nodeInfo file")

        val exitCode = Command(listOf("ls", "/")).run()
        assertThat(exitCode).isEqualTo(0)
    }

    @Test
    fun `successful launch rpc proxy`() {
        val cordaDistribution = Distribution.LATEST_MASTER.path
        val command = Command(listOf("./startRPCproxy.sh", "$cordaDistribution"))
        val exitCode = command.run()
        assertThat(exitCode).isEqualTo(0)

        val pid = Files.lines(Paths.get("/tmp/rpcProxy-pid")).findFirst().get()
        println("Killing RPCProxyServer with pid: $pid")
        Command(listOf("kill", "-9", "$pid")).run()
        FileUtils.deleteQuietly(Paths.get("/tmp/rpcProxy-pid").toFile())
    }

    @Test
    fun `failed command returns non-zero`() {
        val exitCode = Command(listOf("ls", "some-weird-path-that-does-not-exist")).run()
        assertThat(exitCode).isNotEqualTo(0)
    }

    @Test
    fun `output stream for command can be observed`() {
        val subscriber = TestSubscriber<String>()
        val exitCode = Command(listOf("ls", "/")).use { _, output ->
            output.subscribe(subscriber)
            subscriber.awaitTerminalEvent()
            subscriber.assertCompleted()
            subscriber.assertNoErrors()
            assertThat(subscriber.onNextEvents).contains("bin", "etc", "var")
        }
        assertThat(exitCode).isEqualTo(0)
    }

    /**
     * Commands that reproduce the setting up of an R3 Corda Network as per instructions in:
     * https://github.com/corda/enterprise/blob/master/network-management/README.md
     */

    private val source = doormanConfigDirectory
    private val runDir = currentDirectory / "build/runs/doorman"
    private val notaryRunDir = currentDirectory / "build/runs/notary"

    @Test
    fun `step 1 - create key stores for local signer`() {
        println(runDir)
        source.copyRecursively(runDir, true)

        // ROOT
        val commandRoot = JarCommand(Distribution.R3_MASTER.doormanJar,
                                     arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf", "--mode", "ROOT_KEYGEN", "--trust-store-password", "password"),
                                     runDir, 1.minutes)
        assertThat(commandRoot.run()).isEqualTo(0)

        // CA
        val commandCA = JarCommand(Distribution.R3_MASTER.doormanJar,
                                   arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf", "--mode", "CA_KEYGEN"),
                                   runDir, 1.minutes)
        assertThat(commandCA.run()).isEqualTo(0)
    }

    @Test
    fun `step 2 - start doorman service for notary registration`() {
        println(runDir)

        val command = JarCommand(Distribution.R3_MASTER.doormanJar,
                                arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf"),
                                runDir, 10.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    @Test
    fun `step 3 - create notary node and register with the doorman`() {
        println(notaryRunDir)
        val command = JarCommand(Distribution.R3_MASTER.cordaJar,
                                arrayOf("--initial-registration",
                                        "--base-directory", "$notaryRunDir",
                                        "--network-root-truststore", "../doorman/certificates/distribute-nodes/network-root-truststore.jks",
                                        "--network-root-truststore-password", "password"),
                                notaryRunDir, 2.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    @Test
    fun `step 4 - generate node info files for notary nodes`() {
        println(notaryRunDir)
        val command = JarCommand(Distribution.R3_MASTER.cordaJar,
                                arrayOf("--just-generate-node-info",
                                        "--base-directory", "$notaryRunDir"),
                                runDir, 1.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    // step 5 Add notary identities to the network parameters
    // (already configured in template network parameters file)

    @Test
    fun `step 6 - load initial network parameters file for network map service`() {
        println(runDir)
        val command = JarCommand(Distribution.R3_MASTER.doormanJar,
                                arrayOf("--config-file", "$runDir/node.conf", "--update-network-parameters", "$runDir/network-parameters.conf"),
                                runDir, 1.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    @Test
    fun `step 7 - Start a fully configured Doorman NMS`() {
        println(runDir)
        val command = JarCommand(Distribution.R3_MASTER.doormanJar,
                arrayOf("--config-file", "$runDir/node.conf"),
                runDir, 1.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    @Test
    fun `step 8 - initial registration of network participant nodes`() {
        val runDir = currentDirectory / "build/runs/PartyA"
        println(runDir)
        val command = JarCommand(Distribution.R3_MASTER.cordaJar,
                arrayOf("--initial-registration",
                        "--network-root-truststore", "../doorman/certificates/distribute-nodes/network-root-truststore.jks",
                        "--network-root-truststore-password", "password",
                        "--base-directory", "$runDir"),
                runDir, 2.minutes)
        assertThat(command.run()).isEqualTo(0)
    }
}