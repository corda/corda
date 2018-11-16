package com.r3.ha.utilities

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import java.nio.file.Path
import kotlin.test.assertEquals

class BridgeToolTest {

    companion object {
        private val PASSWORD = "password"
    }

    private val sslKeyTool = BridgeSSLKeyTool()

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `tool adds tls key to new bridge store`() {
        val workingDirectory = tempFolder.root.toPath()

        createTLSKeystore(CordaX500Name("NodeA", "London", "GB"), workingDirectory / "nodeA.jks")
        createTLSKeystore(CordaX500Name("NodeB", "London", "GB"), workingDirectory / "nodeB.jks")
        createTLSKeystore(CordaX500Name("NodeC", "London", "GB"), workingDirectory / "nodeC.jks")
        createTLSKeystore(CordaX500Name("NodeD", "London", "GB"), workingDirectory / "nodeD.jks")

        CommandLine.populateCommand(sslKeyTool, "--base-directory", workingDirectory.toString(),
                "--bridge-keystore-password", PASSWORD,
                "--node-keystores", (workingDirectory / "nodeA.jks").toString(), (workingDirectory / "nodeB.jks").toString(), (workingDirectory / "nodeC.jks").toString(), (workingDirectory / "nodeD.jks").toString(),
                "--node-keystore-passwords", PASSWORD)

        Assertions.assertThat(sslKeyTool.baseDirectory).isEqualTo(workingDirectory)
        Assertions.assertThat(sslKeyTool.bridgeKeystore).isEqualTo(workingDirectory / "bridge.jks")

        sslKeyTool.runProgram()
        val keystore = X509KeyStore.fromFile(workingDirectory / "bridge.jks", PASSWORD, createNew = false)
        assertEquals(4, keystore.aliases().asSequence().count())
    }

    private fun createTLSKeystore(name: CordaX500Name, path: Path) {
        val nodeCAKey = Crypto.generateKeyPair()
        val nodeCACert = X509Utilities.createCertificate(CertificateType.NODE_CA, DEV_INTERMEDIATE_CA.certificate, DEV_INTERMEDIATE_CA.keyPair, name.x500Principal, nodeCAKey.public)

        val tlsKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, nodeCACert, nodeCAKey, name.x500Principal, tlsKey.public)

        val certChain = listOf(tlsCert, nodeCACert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate)

        X509KeyStore.fromFile(path, PASSWORD, createNew = true).update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKey.private, certChain, PASSWORD)
        }
    }
}