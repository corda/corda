package net.corda.node.services.rpc

import net.corda.client.rpc.RPCException
import net.corda.client.rpc.internal.RPCClient
import net.corda.core.context.AuthServiceId
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.node.services.messaging.RPCMessagingClient
import net.corda.nodeapi.ArtemisTcpTransport.Companion.tcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.testing.common.internal.withCertificates
import net.corda.testing.common.internal.withKeyStores
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.internal.RandomFree
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

class ArtemisRpcTests {
    private val ports: PortAllocation = RandomFree

    private val user = User("mark", "dadada", setOf(all()))
    private val users = listOf(user)
    private val securityManager = RPCSecurityManagerImpl.fromUserList(AuthServiceId("test"), users)

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    @Test
    fun rpc_with_ssl_enabled() {
        withCertificates { server, client, createSelfSigned, createSignedBy ->
            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))
            val markCertificate = createSignedBy(CordaX500Name("mark", "IT", "R3 London", "London", "London", "GB"), rootCertificate)

            // truststore needs to contain root CA for how the driver works...
            server.keyStore["cordaclienttls"] = rootCertificate
            server.trustStore["cordaclienttls"] = rootCertificate
            server.trustStore["mark"] = markCertificate

            client.keyStore["mark"] = markCertificate
            client.trustStore["cordaclienttls"] = rootCertificate

            withKeyStores(server, client) { brokerSslOptions, clientSslOptions ->
                testSslCommunication(brokerSslOptions, true, clientSslOptions)
            }
        }
    }

    @Test
    fun rpc_with_ssl_disabled() {
        withCertificates { server, client, createSelfSigned, _ ->
            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))

            // truststore needs to contain root CA for how the driver works...
            server.keyStore["cordaclienttls"] = rootCertificate
            server.trustStore["cordaclienttls"] = rootCertificate

            withKeyStores(server, client) { brokerSslOptions, _ ->
                // here server is told not to use SSL, and client sslOptions are null (as in, do not use SSL)
                testSslCommunication(brokerSslOptions, false, null)
            }
        }
    }

    @Test
    fun rpc_with_server_certificate_untrusted_to_client() {
        withCertificates { server, client, createSelfSigned, createSignedBy ->
            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))
            val markCertificate = createSignedBy(CordaX500Name("mark", "IT", "R3 London", "London", "London", "GB"), rootCertificate)

            // truststore needs to contain root CA for how the driver works...
            server.keyStore["cordaclienttls"] = rootCertificate
            server.trustStore["cordaclienttls"] = rootCertificate
            server.trustStore["mark"] = markCertificate

            client.keyStore["mark"] = markCertificate
            // here the server certificate is not trusted by the client
//            client.trustStore["cordaclienttls"] = rootCertificate

            withKeyStores(server, client) { brokerSslOptions, clientSslOptions ->
                testSslCommunication(brokerSslOptions, true, clientSslOptions, clientConnectionSpy = expectExceptionOfType(RPCException::class))
            }
        }
    }

    @Test
    fun rpc_with_no_client_certificate() {
        withCertificates { server, client, createSelfSigned, createSignedBy ->
            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))
            val markCertificate = createSignedBy(CordaX500Name("mark", "IT", "R3 London", "London", "London", "GB"), rootCertificate)

            // truststore needs to contain root CA for how the driver works...
            server.keyStore["cordaclienttls"] = rootCertificate
            server.trustStore["cordaclienttls"] = rootCertificate
            server.trustStore["mark"] = markCertificate

            // here client keystore is empty
//                client.keyStore["mark"] = markCertificate
            client.trustStore["cordaclienttls"] = rootCertificate

            withKeyStores(server, client) { brokerSslOptions, clientSslOptions ->
                testSslCommunication(brokerSslOptions, true, clientSslOptions, clientConnectionSpy = expectExceptionOfType(RPCException::class))
            }
        }
    }

    @Test
    fun rpc_with_no_ssl_on_client_side_and_ssl_on_server_side() {
        withCertificates { server, client, createSelfSigned, createSignedBy ->
            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))
            val markCertificate = createSignedBy(CordaX500Name("mark", "IT", "R3 London", "London", "London", "GB"), rootCertificate)

            // truststore needs to contain root CA for how the driver works...
            server.keyStore["cordaclienttls"] = rootCertificate
            server.trustStore["cordaclienttls"] = rootCertificate
            server.trustStore["mark"] = markCertificate

            client.keyStore["mark"] = markCertificate
            client.trustStore["cordaclienttls"] = rootCertificate

            withKeyStores(server, client) { brokerSslOptions, _ ->
                // here client sslOptions are passed null (as in, do not use SSL)
                testSslCommunication(brokerSslOptions, true, null, clientConnectionSpy = expectExceptionOfType(ActiveMQConnectionTimedOutException::class))
            }
        }
    }

    @Test
    fun rpc_client_certificate_untrusted_to_server() {
        withCertificates { server, client, createSelfSigned, _ ->
            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))
            // here client's certificate is self-signed, otherwise Artemis allows the connection (the issuing certificate is in the truststore)
            val markCertificate = createSelfSigned(CordaX500Name("mark", "IT", "R3 London", "London", "London", "GB"))

            // truststore needs to contain root CA for how the driver works...
            server.keyStore["cordaclienttls"] = rootCertificate
            server.trustStore["cordaclienttls"] = rootCertificate
            // here the client certificate is not trusted by the server
//            server.trustStore["mark"] = markCertificate

            client.keyStore["mark"] = markCertificate
            client.trustStore["cordaclienttls"] = rootCertificate

            withKeyStores(server, client) { brokerSslOptions, clientSslOptions ->
                testSslCommunication(brokerSslOptions, true, clientSslOptions, clientConnectionSpy = expectExceptionOfType(RPCException::class))
            }
        }
    }

    private fun testSslCommunication(brokerSslOptions: SSLConfiguration, useSslForBroker: Boolean, clientSslOptions: SSLConfiguration?, address: NetworkHostAndPort = ports.nextHostAndPort(),
                                     adminAddress: NetworkHostAndPort = ports.nextHostAndPort(), baseDirectory: Path = Files.createTempDirectory(null), clientConnectionSpy: (() -> Unit) -> Unit = {}) {
        val maxMessageSize = 10000
        val jmxEnabled = false
        val certificateChainCheckPolicies: List<CertChainPolicyConfig> = listOf()

        val artemisBroker: ArtemisBroker = if (useSslForBroker) {
            ArtemisRpcBroker.withSsl(address, brokerSslOptions, securityManager, certificateChainCheckPolicies, maxMessageSize, jmxEnabled, baseDirectory)
        } else {
            ArtemisRpcBroker.withoutSsl(address, adminAddress, brokerSslOptions, securityManager, certificateChainCheckPolicies, maxMessageSize, jmxEnabled, baseDirectory)
        }
        artemisBroker.use { broker ->
            broker.start()
            RPCMessagingClient(brokerSslOptions, broker.addresses.admin, maxMessageSize).use { server ->
                server.start(TestRpcOpsImpl(), securityManager, broker.serverControl)

                val client = RPCClient<TestRpcOps>(tcpTransport(ConnectionDirection.Outbound(), broker.addresses.primary, clientSslOptions))

                clientConnectionSpy {
                    client.start(TestRpcOps::class.java, user.username, user.password).use { connection ->
                        connection.proxy.apply {
                            val greeting = greet("Frodo")
                            assertThat(greeting).isEqualTo("Oh, hello Frodo!")
                        }
                    }
                }
            }
        }
    }

    private fun <OPS : RPCOps> RPCMessagingClient.start(ops: OPS, securityManager: RPCSecurityManager, brokerControl: ActiveMQServerControl) {
        apply {
            start(ops, securityManager)
            start2(brokerControl)
        }
    }

    private fun <EXCEPTION : Exception> expectExceptionOfType(exceptionType: KClass<EXCEPTION>): (() -> Unit) -> Unit {
        return { action -> assertThatThrownBy { action.invoke() }.isInstanceOf(exceptionType.java) }
    }

    interface TestRpcOps : RPCOps {
        fun greet(name: String): String
    }

    class TestRpcOpsImpl : TestRpcOps {
        override fun greet(name: String): String = "Oh, hello $name!"

        override val protocolVersion: Int = 1
    }
}