package net.corda.behave.process

import net.corda.behave.file.currentDirectory
import net.corda.behave.file.doormanConfigDirectory
import net.corda.behave.node.Distribution
import net.corda.core.CordaRuntimeException
import net.corda.core.internal.div
import net.corda.core.utilities.minutes
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

class DoormanCommandTests {

    /**
     * Commands that reproduce the setting up of an R3 Corda Network as per instructions in:
     * https://github.com/corda/enterprise/blob/master/network-management/README.md
     */

    private val source = doormanConfigDirectory
    private val doormanRunDir = currentDirectory / "build" / "runs" / "doorman"

    // Set corresponding Java properties to point to valid Corda node configurations
    // eg. -DNOTARY_NODE_DIR=<location of notary node configuration directory>
    private val notaryRunDir = Paths.get(System.getProperty("NOTARY_NODE_DIR") ?: throw CordaRuntimeException("Please set NOTARY_NODE_DIR to point to valid Notary node configuration"))
    private val nodeRunDir = Paths.get(System.getProperty("NODE_DIR") ?: throw CordaRuntimeException("Please set NODE_DIR to point to valid Node configuration"))

    @Test
    fun `step 1 - create key stores for local signer`() {
        println(doormanRunDir)
        source.toFile().copyRecursively(doormanRunDir.toFile(), true)

        // ROOT
        val commandRoot = JarCommand(Distribution.R3_MASTER.doormanJar,
                                     arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf", "--mode", "ROOT_KEYGEN", "--trust-store-password", "password"),
                                     doormanRunDir, 1.minutes)
        assertThat(commandRoot.run()).isEqualTo(0)

        // CA
        val commandCA = JarCommand(Distribution.R3_MASTER.doormanJar,
                                   arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf", "--mode", "CA_KEYGEN"),
                                   doormanRunDir, 1.minutes)
        assertThat(commandCA.run()).isEqualTo(0)
    }

    @Test
    fun `step 2 - start doorman service for notary registration`() {
        println(doormanRunDir)

        val command = JarCommand(Distribution.R3_MASTER.doormanJar,
                                arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf"),
                                doormanRunDir, 10.minutes)
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
                                doormanRunDir, 1.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    // step 5 Add notary identities to the network parameters
    // (already configured in template network parameters file)

    @Test
    fun `step 6 - load initial network parameters file for network map service`() {
        println(doormanRunDir)
        val command = JarCommand(Distribution.R3_MASTER.doormanJar,
                                arrayOf("--config-file", "$doormanRunDir/node.conf", "--set-network-parameters", "$doormanRunDir/network-parameters.conf"),
                                doormanRunDir, 1.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    @Test
    fun `step 7 - Start a fully configured Doorman NMS`() {
        println(doormanRunDir)
        val command = JarCommand(Distribution.R3_MASTER.doormanJar,
                arrayOf("--config-file", "$doormanRunDir/node.conf"),
                doormanRunDir, 1.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    @Test
    fun `step 8 - initial registration of network participant nodes`() {
        println(nodeRunDir)
        val command = JarCommand(Distribution.R3_MASTER.cordaJar,
                arrayOf("--initial-registration",
                        "--network-root-truststore", "../doorman/certificates/distribute-nodes/network-root-truststore.jks",
                        "--network-root-truststore-password", "password",
                        "--base-directory", "$nodeRunDir"),
                doormanRunDir, 2.minutes)
        assertThat(command.run()).isEqualTo(0)
    }
}