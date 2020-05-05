package net.corda.node.amqp.jms

import net.corda.core.context.Trace
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.serialize
import net.corda.core.utilities.Id
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.coretesting.internal.configureTestSSL
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.services.messaging.SimpleMQClient
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.qpid.jms.JmsConnectionFactory
import org.apache.qpid.jms.JmsQueue
import org.apache.qpid.jms.JmsTopic
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.jms.CompletionListener
import javax.jms.ConnectionFactory
import javax.jms.DeliveryMode
import javax.jms.ExceptionListener
import javax.jms.Message
import javax.jms.Session
import kotlin.test.assertTrue

@Ignore
class AmqpRpcTests {

    companion object {
        private val logger = contextLogger()

        const val TAG_FIELD_NAME = "tag" // RPCApi.TAG_FIELD_NAME
        const val RPC_REQUEST = 0// RPCApi.ClientToServer.Tag.RPC_REQUEST

        private const val RPC_ID_FIELD_NAME = "rpc-id" // RPCApi.*
        private const val RPC_ID_TIMESTAMP_FIELD_NAME = "rpc-id-timestamp"
        private const val RPC_SESSION_ID_FIELD_NAME = "rpc-session-id"
        private const val RPC_SESSION_ID_TIMESTAMP_FIELD_NAME = "rpc-session-id-timestamp"
        private const val METHOD_NAME_FIELD_NAME = "method-name"

        private fun Trace.InvocationId.mapTo(message: Message) = mapTo(message, RPC_ID_FIELD_NAME, RPC_ID_TIMESTAMP_FIELD_NAME)

        private fun Trace.SessionId.mapTo(message: Message) = mapTo(message, RPC_SESSION_ID_FIELD_NAME, RPC_SESSION_ID_TIMESTAMP_FIELD_NAME)

        private fun Id<String>.mapTo(message: Message, valueProperty: String, timestampProperty: String) {
            message.setStringProperty(valueProperty, value)
            message.setLongProperty(timestampProperty, timestamp.toEpochMilli())
        }
    }

    @Test(timeout=300_000)
    fun `RPC over AMQP`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode().get()
            val connectionFactory: ConnectionFactory = JmsConnectionFactory("amqp://${node.rpcAddress}")
            val rpcUser = node.rpcUsers.first()
            connectionFactory.createConnection(rpcUser.username, rpcUser.password).use { connection ->

                connection.exceptionListener = ExceptionListener { exception -> logger.error("Exception on connection: $connection", exception) }
                // Start connection
                connection.start()

                // Create a session
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                val rpcServerQueueName = RPCApi.RPC_SERVER_QUEUE_NAME // Note: Server side queue created with MULTICAST routing type,
                // therefore for this "queue" we should use JmsTopic when publishing
                val sender = session.createProducer(JmsTopic(rpcServerQueueName))

                val replyId= Trace.InvocationId.newInstance()
                val sessionId = Trace.SessionId.newInstance()
                val replyQueue = JmsQueue("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.${rpcUser.username}.${random63BitValue()}")
                //session.createTemporaryQueue()

                val message = session.createBytesMessage()
                message.apply {
                    jmsReplyTo = replyQueue
                    setIntProperty(TAG_FIELD_NAME, RPC_REQUEST)
                    replyId.mapTo(this)
                    sessionId.mapTo(this)
                    message.setStringProperty(METHOD_NAME_FIELD_NAME, "currentNodeTime")
                    val serialisedArguments = emptyList<Unit>().serialize(context = AMQP_RPC_CLIENT_CONTEXT)
                    message.writeBytes(serialisedArguments.bytes)
                }

                val sentLatch = CountDownLatch(1)

                logger.info("About to send")
                sender.send(message, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE, object : CompletionListener {
                    override fun onException(message: Message, exception: Exception) {
                        logger.error(message.toString(), exception)
                    }

                    override fun onCompletion(message: Message) {
                        logger.info("Message successfully sent: $message")
                        sentLatch.countDown()
                    }
                })

                assertTrue(sentLatch.await(10, TimeUnit.SECONDS))
                Thread.sleep(10000)
                logger.info("Obtaining response")
                /*
                val receiveQueue = session.createQueue("")
                val consumer: MessageConsumer = session.createConsumer(receiveQueue)

                // Step 7. receive the simple message
                /*val m = */consumer.receive(5000) as BytesMessage
                */
            }
        }
    }

    private fun loginToRPCAndGetClientQueue(nodeHandle: NodeHandle): String {
        val rpcUser = nodeHandle.rpcUsers.first()
        val client = clientTo(nodeHandle.rpcAddress)
        client.start(rpcUser.username, rpcUser.password, false)
        val clientQueueQuery = SimpleString("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.${rpcUser.username}.*")
        return client.session.addressQuery(clientQueueQuery).queueNames.single().toString()
    }

    private fun clientTo(target: NetworkHostAndPort, sslConfiguration: MutualSslConfiguration? = configureTestSSL(CordaX500Name("MegaCorp", "London", "GB"))): SimpleMQClient {
        return SimpleMQClient(target, sslConfiguration)
    }
}