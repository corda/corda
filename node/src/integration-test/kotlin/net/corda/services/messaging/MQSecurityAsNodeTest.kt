package net.corda.services.messaging

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.*
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
class MQSecurityAsNodeTest : MQSecurityTest() {
    override fun createAttacker(): SimpleMQClient {
        return clientTo(alice.internals.configuration.p2pAddress)
    }

    override fun startAttacker(attacker: SimpleMQClient) {
        attacker.start(PEER_USER, PEER_USER)  // Login as a peer
    }

    @Test
    fun `send message to RPC requests address`() {
        assertSendAttackFails(RPCApi.RPC_SERVER_QUEUE_NAME)
    }

    @Test
    fun `only the node running the broker can login using the special node user`() {
        val attacker = clientTo(alice.internals.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_USER, NODE_USER)
        }
    }

    @Test
    fun `login as the default cluster user`() {
        val attacker = clientTo(alice.internals.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQClusterSecurityException::class.java).isThrownBy {
            attacker.start(ActiveMQDefaultConfiguration.getDefaultClusterUser(), ActiveMQDefaultConfiguration.getDefaultClusterPassword())
        }
    }

    @Test
    fun `login without a username and password`() {
        val attacker = clientTo(alice.internals.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start()
        }
    }

    @Test
    fun `login to a non ssl port as a node user`() {
        val attacker = clientTo(alice.internals.configuration.rpcAddress!!, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_USER, NODE_USER, enableSSL = false)
        }
    }

    @Test
    fun `login to a non ssl port as a peer user`() {
        val attacker = clientTo(alice.internals.configuration.rpcAddress!!, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER, enableSSL = false)  // Login as a peer
        }
    }

    @Test
    fun `login with invalid certificate chain`() {
        val sslConfig = object : SSLConfiguration {
            override val certificatesDirectory = Files.createTempDirectory("certs")
            override val keyStorePassword: String get() = "cordacadevpass"
            override val trustStorePassword: String get() = "trustpass"

            init {
                val legalName = CordaX500Name("MegaCorp", "London", "GB")
                certificatesDirectory.createDirectories()
                if (!trustStoreFile.exists()) {
                    javaClass.classLoader.getResourceAsStream("certificates/cordatruststore.jks").copyTo(trustStoreFile)
                }

                val caKeyStore = loadKeyStore(
                        javaClass.classLoader.getResourceAsStream("certificates/cordadevcakeys.jks"),
                        "cordacadevpass")

                val rootCACert = caKeyStore.getX509Certificate(X509Utilities.CORDA_ROOT_CA).toX509CertHolder()
                val intermediateCA = caKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_INTERMEDIATE_CA, "cordacadevkeypass")
                val clientKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)

                // Set name constrain to the legal name.
                val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, legalName.x500Name))), arrayOf())
                val clientCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, intermediateCA.certificate,
                        intermediateCA.keyPair, legalName, clientKey.public, nameConstraints = nameConstraints)
                val tlsKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                // Using different x500 name in the TLS cert which is not allowed in the name constraints.
                val clientTLSCert = X509Utilities.createCertificate(CertificateType.TLS, clientCACert, clientKey, CordaX500Name("MiniCorp", "London", "GB"), tlsKey.public)
                val keyPass = keyStorePassword.toCharArray()
                val clientCAKeystore = loadOrCreateKeyStore(nodeKeystore, keyStorePassword)
                clientCAKeystore.addOrReplaceKey(
                        X509Utilities.CORDA_CLIENT_CA,
                        clientKey.private,
                        keyPass,
                        arrayOf(clientCACert, intermediateCA.certificate, rootCACert))
                clientCAKeystore.save(nodeKeystore, keyStorePassword)

                val tlsKeystore = loadOrCreateKeyStore(sslKeystore, keyStorePassword)
                tlsKeystore.addOrReplaceKey(
                        X509Utilities.CORDA_CLIENT_TLS,
                        tlsKey.private,
                        keyPass,
                        arrayOf(clientTLSCert, clientCACert, intermediateCA.certificate, rootCACert))
                tlsKeystore.save(sslKeystore, keyStorePassword)
            }
        }

        val attacker = clientTo(alice.internals.configuration.p2pAddress, sslConfig)

        assertThatExceptionOfType(ActiveMQNotConnectedException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER)
        }
    }
}
