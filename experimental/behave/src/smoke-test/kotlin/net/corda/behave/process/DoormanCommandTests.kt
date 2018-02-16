package net.corda.behave.process

import net.corda.behave.file.currentDirectory
import net.corda.behave.file.doormanConfigDirectory
import net.corda.behave.network.Network
import net.corda.behave.node.Distribution
import net.corda.behave.node.configuration.NotaryType
import net.corda.core.internal.div
import net.corda.core.utilities.minutes
import org.assertj.core.api.Assertions.assertThat
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

class DoormanCommandTests {

    /**
     * Commands that reproduce the setting up of an R3 Corda Network as per instructions in:
     * https://github.com/corda/enterprise/blob/master/network-management/README.md
     */

    private val source = doormanConfigDirectory
    private val doormanRunDir = currentDirectory / "build/runs/doorman"

    // TODO: use environment variables to specify build location & SQL driver to use
    // Configure the following to point to valid Corda node configurations
    private val notaryRunDir = currentDirectory / "build" / "runs" / "Notary"
    private val participantRunDir = currentDirectory / "build" / "runs" / "PartyA"

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
        println(participantRunDir)
        val command = JarCommand(Distribution.R3_MASTER.cordaJar,
                arrayOf("--initial-registration",
                        "--network-root-truststore", "../doorman/certificates/distribute-nodes/network-root-truststore.jks",
                        "--network-root-truststore-password", "password",
                        "--base-directory", "$participantRunDir"),
                doormanRunDir, 2.minutes)
        assertThat(command.run()).isEqualTo(0)
    }
}