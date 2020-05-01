package net.corda.node.amqp.jms

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.coretesting.internal.configureTestSSL
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.services.messaging.SimpleMQClient
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.qpid.jms.JmsConnectionFactory
import org.apache.qpid.jms.JmsQueue
import org.junit.Ignore
import org.junit.Test
import javax.jms.BytesMessage
import javax.jms.ConnectionFactory
import javax.jms.MessageConsumer
import javax.jms.Session

@Ignore
class AmqpRpcTests {
    private val CORDAPPS = listOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP) // TODO: Do we need CordApps at all?

    @Test(timeout=300_000)
    fun `RPC over AMQP`() {
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = CORDAPPS, extraCordappPackagesToScan = emptyList())) {
            val node = startNode().get()
            val connectionFactory: ConnectionFactory = JmsConnectionFactory("amqp://${node.rpcAddress}")
            val rpcUser = node.rpcUsers.first()
            connectionFactory.createConnection(rpcUser.username, rpcUser.password).use { connection ->

                // Create a session
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

                val clientQueueName = loginToRPCAndGetClientQueue(node)
                val sender = session.createProducer(JmsQueue(clientQueueName))
                //val message = session.createBytesMessage()

                /*
                val serialisedArguments = emptyList().serialize(context = serializationContextWithObservableContext)
                val request = RPCApi.ClientToServer.RpcRequest(
                        clientAddress,
                        "currentNodeTime",
                        serialisedArguments,
                        replyId,
                        sessionId,
                        externalTrace,
                        impersonatedActor
                )
                 */

                sender.send(session.createTextMessage("Hello world "))
                connection.start()

                val receiveQueue = session.createQueue("")
                val consumer: MessageConsumer = session.createConsumer(receiveQueue)

                // Step 7. receive the simple message
                /*val m = */consumer.receive(5000) as BytesMessage
            }
        }
    }

    private fun loginToRPCAndGetClientQueue(nodeHandle: NodeHandle): String {
        val rpcUser = nodeHandle.rpcUsers.first()
        val clientQueueQuery = SimpleString("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.${rpcUser.username}.*")
        val client = clientTo(nodeHandle.rpcAddress)
        client.start(rpcUser.username, rpcUser.password, false)
        return client.session.addressQuery(clientQueueQuery).queueNames.single().toString()
    }

    private fun clientTo(target: NetworkHostAndPort, sslConfiguration: MutualSslConfiguration? = configureTestSSL(CordaX500Name("MegaCorp", "London", "GB"))): SimpleMQClient {
        return SimpleMQClient(target, sslConfiguration)
    }
}