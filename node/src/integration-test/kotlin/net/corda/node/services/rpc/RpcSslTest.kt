package net.corda.node.services.rpc

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.internal.div
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.utilities.createKeyPairAndSelfSignedTLSCertificate
import net.corda.node.utilities.saveToKeyStore
import net.corda.node.utilities.saveToTrustStore
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_RPC_USER
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.internal.useSslRpcOverrides
import net.corda.testing.node.User
import org.apache.activemq.artemis.api.core.ActiveMQException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.security.auth.x500.X500Principal

class RpcSslTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val testName = X500Principal("CN=Test,O=R3 Ltd,L=London,C=GB")

    @Test
    fun `RPC client using ssl is able to run a command`() {
        val user = User("mark", "dadada", setOf(all()))
        var successfulLogin = false
        var failedLogin = false

        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")

        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow()
            val client = CordaRPCClient.createWithSsl(node.rpcAddress, sslConfiguration = clientSslOptions)
            val connection = client.start(user.username, user.password)

            connection.proxy.apply {
                val nodeInfo = nodeInfo()
                assertThat(nodeInfo.legalIdentities).isNotEmpty
                successfulLogin = true
            }
            connection.close()

            Assertions.assertThatThrownBy {
                val connection2 = CordaRPCClient.createWithSsl(node.rpcAddress, sslConfiguration = clientSslOptions).start(user.username, "wrong")
                connection2.proxy.apply {
                    nodeInfo()
                    failedLogin = true
                }
                connection2.close()
            }.isInstanceOf(ActiveMQSecurityException::class.java)
        }
        assertThat(successfulLogin).isTrue()
        assertThat(failedLogin).isFalse()
    }

    @Test
    fun `RPC client using ssl will fail if connecting to a node that cannot present a matching certificate`() {
        val user = User("mark", "dadada", setOf(all()))
        var successful = false

        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")

        val (_, cert1) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert1)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow()
            Assertions.assertThatThrownBy {
                val connection = CordaRPCClient.createWithSsl(node.rpcAddress, sslConfiguration = clientSslOptions).start(user.username, user.password)
                connection.proxy.apply {
                    nodeInfo()
                    successful = true
                }
                connection.close()
            }.isInstanceOf(RPCException::class.java)

        }

        assertThat(successful).isFalse()
    }

    @Test
    fun `RPC client not using ssl can run commands`() {
        val user = User("mark", "dadada", setOf(all()))
        var successful = false
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(rpcUsers = listOf(user)).getOrThrow()
            val connection = CordaRPCClient(node.rpcAddress).start(user.username, user.password)
            connection.proxy.apply {
                nodeInfo()
                successful = true
            }
            connection.close()
        }
        assertThat(successful).isTrue()
    }

    @Test
    fun `The system RPC user can not connect to the rpc broker without the node's key`() {
        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")
        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow()
            val client = CordaRPCClient.createWithSsl(node.rpcAddress, sslConfiguration = clientSslOptions)

            Assertions.assertThatThrownBy {
                client.start(NODE_RPC_USER, NODE_RPC_USER).use { connection ->
                    connection.proxy.nodeInfo()
                }
            }.isInstanceOf(ActiveMQException::class.java)

            val clientAdmin = CordaRPCClient.createWithSsl(node.rpcAdminAddress, sslConfiguration = clientSslOptions)

            Assertions.assertThatThrownBy {
                clientAdmin.start(NODE_RPC_USER, NODE_RPC_USER).use { connection ->
                    connection.proxy.nodeInfo()
                }
            }.isInstanceOf(RPCException::class.java)
        }
    }

}