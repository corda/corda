package com.r3.ha.utilities

import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.cliutils.ExitCodes
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationToolTest {

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val registrationTool = RegistrationTool()

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
            assertTrue("Could not find sslkeystore.js for $it") {(workingDirectory / it / "certificates" / "sslkeystore.jks").exists()}
            assertTrue("Could not find truststore.js for $it") {(workingDirectory / it / "certificates" / "truststore.jks").exists()}
            assertTrue("Could not find nodekeystore.js for $it") {(workingDirectory / it / "certificates" / "nodekeystore.jks").exists()}
        }

        assertTrue((workingDirectory / NETWORK_PARAMS_FILE_NAME).exists())
    }
}