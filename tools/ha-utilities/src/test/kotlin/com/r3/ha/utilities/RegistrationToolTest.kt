package com.r3.ha.utilities

import com.r3.ha.utilities.RegistrationTool.Companion.toFolderName
import com.r3.ha.utilities.RegistrationTool.Companion.x500PrincipalToTLSAlias
import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.cliutils.ExitCodes
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
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
import java.io.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationToolTest {

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val registrationTool = RegistrationTool()
    private val portAllocation = incrementalPortAllocation(20101) // TODO: During `master` merge remove 20101

    private val nmPort = portAllocation.nextPort()

    @Rule
    @JvmField
    val bridgeHSM = HsmSimulator(portAllocation)

    private val utimacoPort = bridgeHSM.address.port

    private val portReplacementMap = mapOf("\$NM_PORT" to nmPort.toString(), "\$UTIMACO_PORT" to utimacoPort.toString())

    companion object {

        private val log = contextLogger()

        private fun InputStream.replaceMapping(replacement: Map<String, String>): InputStream {
            val text = InputStreamReader(this).readText()
            val textWithReplacementApplied = replacement.entries.fold(text) { currText, entry -> currText.replace(entry.key, entry.value) }
            return ByteArrayInputStream(textWithReplacementApplied.toByteArray())
        }
    }

    init {
        log.info("NM port: $nmPort")
        log.info("Utimaco port: $utimacoPort")
    }

    @Test
    fun `the tool can register multiple nodes at the same time`() {
        val workingDirectory = tempFolder.root.toPath()
        // create network trust root trust store
        val trustStorePath = workingDirectory / "networkTrustRootStore.jks"
        X509KeyStore.fromFile(trustStorePath, "password", true).update {
            setCertificate(X509Utilities.CORDA_ROOT_CA, DEV_ROOT_CA.certificate)
        }

        val workingDirReplacementMap = mapOf("\$WORKING_DIR" to workingDirectory.toString())

        listOf("nodeA.conf", "nodeB.conf", "nodeC.conf", "nodeD.conf", "utimaco_config.yml").forEach {
            javaClass.classLoader.getResourceAsStream(it).replaceMapping(portReplacementMap)
                    .replaceMapping(workingDirReplacementMap).copyTo(workingDirectory / it)
        }

        val runResult = RegistrationServer(NetworkHostAndPort("localhost", nmPort)).use {
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

        listOf("PartyA", "Party_B", "PartyC").forEach {
            assertTrue(it) {(workingDirectory / it / "certificates" / "sslkeystore.jks").exists()}
            assertTrue(it) {(workingDirectory / it / "certificates" / "truststore.jks").exists()}
            assertTrue(it) {(workingDirectory / it / "certificates" / "nodekeystore.jks").exists()}
            assertTrue(it) {(workingDirectory / it / "certificates" / "wrappingkeystore.pkcs12").exists()}
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

        listOf("nodeA.conf", "nodeB.conf", "nodeC.conf").forEach {
            javaClass.classLoader.getResourceAsStream(it).replaceMapping(portReplacementMap).copyTo(workingDirectory / it)
        }

        // Store firewall files in a separate directory
        val firewallDir = workingDirectory / "firewall"
        firewallDir.toFile().mkdir()
        listOf("firewall.conf", "utimaco_config.yml").forEach {
            javaClass.classLoader.getResourceAsStream(it).replaceMapping(portReplacementMap).copyTo(firewallDir / it)
        }

        val runResult = RegistrationServer(NetworkHostAndPort("localhost", nmPort)).use {
            it.start()
            CommandLine.populateCommand(registrationTool, BASE_DIR, workingDirectory.toString(),
                    "--network-root-truststore", trustStorePath.toString(),
                    "--network-root-truststore-password", "password",
                    "--bridge-keystore-password", "password",
                    "--bridge-config-file", (firewallDir / "firewall.conf").toString(),
                    "--config-files", (workingDirectory / "nodeA.conf").toString(), (workingDirectory / "nodeB.conf").toString(),
                    (workingDirectory / "nodeC.conf").toString())
            registrationTool.runProgram()
        }

        assertEquals(ExitCodes.SUCCESS, runResult)

        val legalNames = listOf("PartyA", "Party B", "PartyC").map { CordaX500Name(it, "London", "GB") }

        legalNames.forEach {
            val certsPath = workingDirectory / it.toFolderName() / "certificates"
            assertTrue(it.toString()) {(certsPath / "sslkeystore.jks").exists()}
            assertTrue(it.toString()) {(certsPath / "truststore.jks").exists()}
            assertTrue(it.toString()) {(certsPath / "nodekeystore.jks").exists()}
        }

        assertTrue((workingDirectory / "bridge.jks").exists())

        // Compare the contents of each keystore against the bridge and bridge HSM
        val cryptoService = UtimacoCryptoService.fromConfigurationFile((firewallDir / "utimaco_config.yml"))
        val bridgeKeyStore = X509KeyStore.fromFile((workingDirectory / "bridge.jks"), "password", createNew = false)
        legalNames.forEach {
            val alias = x500PrincipalToTLSAlias(it.x500Principal)
            val sslKeyStore = X509KeyStore.fromFile((workingDirectory / it.toFolderName() / "certificates" / "sslkeystore.jks"), "cordacadevpass", createNew = false)
            assertTrue(bridgeKeyStore.contains(alias))
            assertEquals(sslKeyStore.getPublicKey(CORDA_CLIENT_TLS), bridgeKeyStore.getPublicKey(alias))
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
            javaClass.classLoader.getResourceAsStream(it).replaceMapping(portReplacementMap).copyTo(workingDirectory / it)
        }

        val runResult = RegistrationServer(NetworkHostAndPort("localhost", nmPort)).use {
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