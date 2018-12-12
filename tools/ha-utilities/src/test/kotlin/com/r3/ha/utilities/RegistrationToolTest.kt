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
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import kotlin.test.assertEquals
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

        javaClass.classLoader.getResourceAsStream("nodeA.conf").copyTo(workingDirectory / "nodeA.conf")
        javaClass.classLoader.getResourceAsStream("nodeB.conf").copyTo(workingDirectory / "nodeB.conf")
        javaClass.classLoader.getResourceAsStream("nodeC.conf").copyTo(workingDirectory / "nodeC.conf")

        val runResult = RegistrationServer(NetworkHostAndPort("localhost", 10000)).use {
            it.start()
            CommandLine.populateCommand(registrationTool, BASE_DIR, workingDirectory.toString(),
                    "--network-root-truststore", trustStorePath.toString(),
                    "--network-root-truststore-password", "password",
                    "--config-files", (workingDirectory / "nodeA.conf").toString(), (workingDirectory / "nodeB.conf").toString(), (workingDirectory / "nodeC.conf").toString())
            registrationTool.runProgram()
        }

        assertEquals(ExitCodes.SUCCESS, runResult)

        assertTrue((workingDirectory / "PartyA" / "certificates" / "sslkeystore.jks").exists())
        assertTrue((workingDirectory / "PartyB" / "certificates" / "sslkeystore.jks").exists())
        assertTrue((workingDirectory / "PartyC" / "certificates" / "sslkeystore.jks").exists())

        assertTrue((workingDirectory / "PartyA" / "certificates" / "truststore.jks").exists())
        assertTrue((workingDirectory / "PartyB" / "certificates" / "truststore.jks").exists())
        assertTrue((workingDirectory / "PartyC" / "certificates" / "truststore.jks").exists())

        assertTrue((workingDirectory / "PartyA" / "certificates" / "nodekeystore.jks").exists())
        assertTrue((workingDirectory / "PartyB" / "certificates" / "nodekeystore.jks").exists())
        assertTrue((workingDirectory / "PartyC" / "certificates" / "nodekeystore.jks").exists())

        assertTrue((workingDirectory / NETWORK_PARAMS_FILE_NAME).exists())
    }
}
