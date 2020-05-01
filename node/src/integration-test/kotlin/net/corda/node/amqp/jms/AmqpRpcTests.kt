package net.corda.node.amqp.jms

import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import org.apache.qpid.jms.JmsConnectionFactory
import org.junit.Test
import javax.jms.BytesMessage
import javax.jms.ConnectionFactory
import javax.jms.MessageConsumer
import javax.jms.Session

class AmqpRpcTests {
    private val CORDAPPS = listOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP) // TODO: Do we need CordApps at all?

    @Test(timeout=300_000)
    fun `RPC over AMQP`() {
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = CORDAPPS, extraCordappPackagesToScan = emptyList())) {
            val node = startNode().get()
            val connectionFactory: ConnectionFactory = JmsConnectionFactory("amqp://${node.rpcAddress}")

            connectionFactory.createConnection().use { connection ->

                // Create a session
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

                val sendQueue = session.createQueue("exampleQueue")
                val sender = session.createProducer(sendQueue)
                val message = session.createBytesMessage()

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
                val m = consumer.receive(5000) as BytesMessage
            }
        }
    }
}