package net.corda.node.services.rpc

import net.corda.client.rpc.RPCException
import net.corda.client.rpc.internal.RPCClient
import net.corda.core.context.AuthServiceId
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.messaging.InternalRPCMessagingClient
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.node.utilities.createKeyPairAndSelfSignedTLSCertificate
import net.corda.node.utilities.saveToKeyStore
import net.corda.node.utilities.saveToTrustStore
import net.corda.nodeapi.ArtemisTcpTransport.Companion.rpcConnectorTcpTransport
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.TwoWaySslConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.p2pSslConfiguration
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import javax.security.auth.x500.X500Principal

class ArtemisRpcTests {
    private val ports: PortAllocation = PortAllocation.Incremental(10000)

    private val user = User("mark", "dadada", setOf(all()))
    private val users = listOf(user)
    private val securityManager = RPCSecurityManagerImpl.fromUserList(AuthServiceId("test"), users)

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    val testName = X500Principal("CN=Test,O=R3 Ltd,L=London,C=GB")

    @Test
    fun rpc_with_ssl_enabled() {
        val (rpcKeyPair, selfSignCert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFile("rpcKeystore.jks"), rpcKeyPair, selfSignCert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")
        val trustStorePath = saveToTrustStore(tempFile("rpcTruststore.jks"), selfSignCert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")
        testSslCommunication(p2pSslConfiguration(tempFolder.root.toPath()), brokerSslOptions, true, clientSslOptions)
    }

    @Test
    fun rpc_with_ssl_disabled() {
        testSslCommunication(p2pSslConfiguration(tempFolder.root.toPath()), null, false, null)
    }

    @Test
    fun rpc_with_no_ssl_on_client_side_and_ssl_on_server_side() {
        val (rpcKeyPair, selfSignCert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFile("rpcKeystore.jks"), rpcKeyPair, selfSignCert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")
        // here client sslOptions are passed null (as in, do not use SSL)
        assertThatThrownBy {
            testSslCommunication(p2pSslConfiguration(tempFolder.root.toPath()), brokerSslOptions, true, null)
        }.isInstanceOf(ActiveMQConnectionTimedOutException::class.java)
    }

    @Test
    fun rpc_client_certificate_untrusted_to_server() {
        val (rpcKeyPair, selfSignCert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFile("rpcKeystore.jks"), rpcKeyPair, selfSignCert)

        // create another keypair and certificate and add that to the client truststore
        // the ssl connection should not
        val (_, selfSignCert1) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val trustStorePath = saveToTrustStore(tempFile("rpcTruststore.jks"), selfSignCert1)

        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        assertThatThrownBy {
            testSslCommunication(p2pSslConfiguration(tempFolder.root.toPath()), brokerSslOptions, true, clientSslOptions)
        }.isInstanceOf(RPCException::class.java)
    }

    private fun testSslCommunication(nodeSSlconfig: TwoWaySslConfiguration,
                                     brokerSslOptions: BrokerRpcSslOptions?,
                                     useSslForBroker: Boolean,
                                     clientSslOptions: ClientRpcSslOptions?,
                                     address: NetworkHostAndPort = ports.nextHostAndPort(),
                                     adminAddress: NetworkHostAndPort = ports.nextHostAndPort(),
                                     baseDirectory: Path = tempFolder.root.toPath()
    ) {
        val maxMessageSize = 10000
        val jmxEnabled = false

        val artemisBroker: ArtemisBroker = if (useSslForBroker) {
            ArtemisRpcBroker.withSsl(nodeSSlconfig, address, adminAddress, brokerSslOptions!!, securityManager, maxMessageSize, jmxEnabled, baseDirectory, false)
        } else {
            ArtemisRpcBroker.withoutSsl(nodeSSlconfig, address, adminAddress, securityManager, maxMessageSize, jmxEnabled, baseDirectory, false)
        }
        artemisBroker.use { broker ->
            broker.start()
            InternalRPCMessagingClient(nodeSSlconfig, adminAddress, maxMessageSize, CordaX500Name("MegaCorp", "London", "GB"), RPCServerConfiguration.DEFAULT).use { server ->
                server.start(TestRpcOpsImpl(), securityManager, broker.serverControl)

                val client = RPCClient<TestRpcOps>(rpcConnectorTcpTransport(broker.addresses.primary, clientSslOptions))

                val greeting = client.start(TestRpcOps::class.java, user.username, user.password).use { connection ->
                    connection.proxy.greet("Frodo")
                }
                assertThat(greeting).isEqualTo("Oh, hello Frodo!")
            }
        }
    }

    private fun <OPS : RPCOps> InternalRPCMessagingClient.start(ops: OPS, securityManager: RPCSecurityManager, brokerControl: ActiveMQServerControl) {
        apply {
            init(ops, securityManager)
            start(brokerControl)
        }
    }

    interface TestRpcOps : RPCOps {
        fun greet(name: String): String
    }

    class TestRpcOpsImpl : TestRpcOps {
        override fun greet(name: String): String = "Oh, hello $name!"

        override val protocolVersion: Int = 1
    }

    private fun tempFile(name: String): Path = tempFolder.root.toPath() / name

}