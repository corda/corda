package net.corda.behave.process

import net.corda.behave.file.currentDirectory
import net.corda.behave.file.div
import net.corda.behave.file.doormanConfigDirectory
import net.corda.behave.node.Distribution
import net.corda.core.utilities.minutes
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DoormanCommandTests {

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
                                arrayOf("--config-file", "$runDir/node.conf", "--set-network-parameters", "$runDir/network-parameters.conf"),
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