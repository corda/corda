package net.corda.services.messaging

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.internal.exists
import net.corda.core.internal.toX500Name
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_P2P_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.loadDevCaTrustStore
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.ActiveMQClusterSecurityException
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.junit.Test
import java.nio.file.Files

/**
 * Runs the security tests with the attacker pretending to be a node on the network.
 */
class MQSecurityAsNodeTest : P2PMQSecurityTest() {
    override fun createAttacker(): SimpleMQClient {
        return clientTo(alice.node.configuration.p2pAddress)
    }

    override fun startAttacker(attacker: SimpleMQClient) {
        attacker.start(PEER_USER, PEER_USER)  // Login as a peer
    }

    @Test
    fun `send message to RPC requests address`() {
        assertSendAttackFails(RPCApi.RPC_SERVER_QUEUE_NAME)
    }

    @Test
    fun `only the node running the broker can login using the special P2P node user`() {
        val attacker = clientTo(alice.node.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_P2P_USER, NODE_P2P_USER)
        }
    }

    @Test
    fun `login as the default cluster user`() {
        val attacker = clientTo(alice.node.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQClusterSecurityException::class.java).isThrownBy {
            attacker.start(ActiveMQDefaultConfiguration.getDefaultClusterUser(), ActiveMQDefaultConfiguration.getDefaultClusterPassword())
        }
    }

    @Test
    fun `login without a username and password`() {
        val attacker = clientTo(alice.node.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start()
        }
    }

    @Test
    fun `login to a non ssl port as a node user`() {
        val attacker = clientTo(alice.node.configuration.rpcOptions.address, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_P2P_USER, NODE_P2P_USER, enableSSL = false)
        }
    }

    @Test
    fun `login to a non ssl port as a peer user`() {
        val attacker = clientTo(alice.node.configuration.rpcOptions.address, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER, enableSSL = false)  // Login as a peer
        }
    }

    @Test
    fun `login with invalid certificate chain`() {
        val certsDir = Files.createTempDirectory("certs")
        certsDir.createDirectories()
        val signingCertStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certsDir)
        val p2pSslConfig = CertificateStoreStubs.P2P.withCertificatesDirectory(certsDir)

        val legalName = CordaX500Name("MegaCorp", "London", "GB")
        if (!p2pSslConfig.trustStore.path.exists()) {
            val trustStore = p2pSslConfig.trustStore.get(true)
            loadDevCaTrustStore().copyTo(trustStore)
        }

        val clientKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        // Set name constrain to the legal name.
        val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, legalName.toX500Name()))), arrayOf())
        val clientCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, DEV_INTERMEDIATE_CA.certificate, DEV_INTERMEDIATE_CA.keyPair, legalName.x500Principal, clientKeyPair.public, nameConstraints = nameConstraints)

        val tlsKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        // Using different x500 name in the TLS cert which is not allowed in the name constraints.
        val clientTLSCert = X509Utilities.createCertificate(CertificateType.TLS, clientCACert, clientKeyPair, CordaX500Name("MiniCorp", "London", "GB").x500Principal, tlsKeyPair.public)

        signingCertStore.get(createNew = true).update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_CA, clientKeyPair.private, listOf(clientCACert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate))
        }

        p2pSslConfig.keyStore.get(createNew = true).update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeyPair.private, listOf(clientTLSCert, clientCACert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate))
        }

        val attacker = clientTo(alice.node.configuration.p2pAddress, p2pSslConfig)
        assertThatExceptionOfType(ActiveMQNotConnectedException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER)
        }
    }
}
