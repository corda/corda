package com.r3.ha.utilities

import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.cliutils.ExitCodes
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService
import net.corda.nodeapi.internal.hsm.HsmSimulator
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationToolTest {

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val registrationTool = RegistrationTool()
    private val portAllocation = incrementalPortAllocation(25666)

    @Rule
    @JvmField
    val bridgeHSM = HsmSimulator(portAllocation)

    @Test
    fun `the tool can register multiple nodes at the same time`() {
        val workingDirectory = tempFolder.root.toPath()
        // create network trust root trust store
        val trustStorePath = workingDirectory / "networkTrustRootStore.jks"
        X509KeyStore.fromFile(trustStorePath, "password", true).update {
            setCertificate(X509Utilities.CORDA_ROOT_CA, DEV_ROOT_CA.certificate)
        }

        listOf("nodeA.conf", "nodeB.conf", "nodeC.conf", "nodeD.conf").forEach {
            javaClass.classLoader.getResourceAsStream(it).copyTo(workingDirectory / it)
        }

        val runResult = RegistrationServer(NetworkHostAndPort("localhost", 10000)).use {
            it.start()
            CommandLine.populateCommand(registrationTool, BASE_DIR, workingDirectory.toString(),
                    "--network-root-truststore", trustStorePath.toString(),
                    "--network-root-truststore-password", "password",
                    "--config-files", (workingDirectory / "nodeA.conf").toString(), (workingDirectory / "nodeB.conf").toString(),
                    (workingDirectory / "nodeC.conf").toString(), (workingDirectory / "nodeD.conf").toString())
            registrationTool.runProgram()
        }

        assertEquals(ExitCodes.FAILURE, runResult)

        assertFalse((workingDirectory / "PartyD" / "certificates" / "sslkeystore.jks").exists())

        listOf("PartyA", "PartyB", "PartyC").forEach {
            assertTrue(it) {(workingDirectory / it / "certificates" / "sslkeystore.jks").exists()}
            assertTrue(it) {(workingDirectory / it / "certificates" / "truststore.jks").exists()}
            assertTrue(it) {(workingDirectory / it / "certificates" / "nodekeystore.jks").exists()}
        }

        assertTrue((workingDirectory / NETWORK_PARAMS_FILE_NAME).exists())
    }

    @Test
    fun `register nodes and bridge using HSM`() {
        val workingDirectory = tempFolder.root.toPath()

        // create network trust root trust store
        val trustStorePath = workingDirectory / "networkTrustRootStore.jks"
        X509KeyStore.fromFile(trustStorePath, "password", true).update {
            setCertificate(X509Utilities.CORDA_ROOT_CA, DEV_ROOT_CA.certificate)
        }

        listOf("nodeA.conf", "nodeB.conf", "nodeC.conf", "firewall.conf", "utimaco_config.yml").forEach {
            javaClass.classLoader.getResourceAsStream(it).copyTo(workingDirectory / it)
        }

        val runResult = RegistrationServer(NetworkHostAndPort("localhost", 10000)).use {
            it.start()
            CommandLine.populateCommand(registrationTool, BASE_DIR, workingDirectory.toString(),
                    "--network-root-truststore", trustStorePath.toString(),
                    "--network-root-truststore-password", "password",
                    "--bridge-keystore-password", "password",
                    "--bridge-config-file", (workingDirectory / "firewall.conf").toString(),
                    "--config-files", (workingDirectory / "nodeA.conf").toString(), (workingDirectory / "nodeB.conf").toString(),
                    (workingDirectory / "nodeC.conf").toString())
            registrationTool.runProgram()
        }

        assertEquals(ExitCodes.SUCCESS, runResult)

        listOf("PartyA", "PartyB", "PartyC").forEach {
            assertTrue(it) {(workingDirectory / it / "certificates" / "sslkeystore.jks").exists()}
            assertTrue(it) {(workingDirectory / it / "certificates" / "truststore.jks").exists()}
            assertTrue(it) {(workingDirectory / it / "certificates" / "nodekeystore.jks").exists()}
        }

        assertTrue((workingDirectory / "bridge.jks").exists())

        // Compare the contents of each keystore against the bridge and bridge HSM
        val cryptoService = UtimacoCryptoService.fromConfigurationFile((workingDirectory / "utimaco_config.yml"))
        val bridgeKeyStore = X509KeyStore.fromFile((workingDirectory / "bridge.jks"), "password", createNew = false)
        listOf("PartyA", "PartyB", "PartyC").forEach {
            val nameHash = SecureHash.sha256(X500Principal("O=$it,L=London,C=GB").toString())
            val alias = "$CORDA_CLIENT_TLS-$nameHash"
            val nodeKeyStore = X509KeyStore.fromFile((workingDirectory / it / "certificates" / "sslkeystore.jks"), "cordacadevpass", createNew = false)
            assertTrue(bridgeKeyStore.contains(alias))
            assertEquals(nodeKeyStore.getPublicKey(CORDA_CLIENT_TLS), bridgeKeyStore.getPublicKey(alias))
            assertTrue(cryptoService.containsKey(alias))
            assertEquals(bridgeKeyStore.getPublicKey(alias), cryptoService.getPublicKey(alias))
        }
    }

    @Test
    fun `register multiple nodes with conflicting crypto services configurations`() {
        val workingDirectory = tempFolder.root.toPath()

        // create network trust root trust store
        val trustStorePath = workingDirectory / "networkTrustRootStore.jks"
        X509KeyStore.fromFile(trustStorePath, "password", true).update {
            setCertificate(X509Utilities.CORDA_ROOT_CA, DEV_ROOT_CA.certificate)
        }

        listOf("nodeA_HSM.conf", "nodeB_HSM.conf", "nodeC_HSM.conf", "firewall.conf", "utimaco_config.yml", "utimaco_config2.yml").forEach {
            javaClass.classLoader.getResourceAsStream(it).copyTo(workingDirectory / it)
        }

        val runResult = RegistrationServer(NetworkHostAndPort("localhost", 10000)).use {
            it.start()
            CommandLine.populateCommand(registrationTool, BASE_DIR, workingDirectory.toString(),
                    "--network-root-truststore", trustStorePath.toString(),
                    "--network-root-truststore-password", "password",
                    "--bridge-keystore-password", "password",
                    "--bridge-config-file", (workingDirectory / "firewall.conf").toString(),
                    "--config-files", (workingDirectory / "nodeA_HSM.conf").toString(), (workingDirectory / "nodeB_HSM.conf").toString(),
                    (workingDirectory / "nodeC_HSM.conf").toString())
            registrationTool.runProgram()
        }

        assertEquals(ExitCodes.FAILURE, runResult)
    }
}