package net.corda.node

import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.serialization.serialize
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.rpc.client.ObservableContext
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import org.apache.activemq.artemis.jms.client.ActiveMQSession
import org.junit.Test
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.TextMessage
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.qpid.jms.JmsConnectionFactory;
import rx.Observable
import java.util.*
import javax.jms.BytesMessage
import javax.jms.Connection
import javax.jms.ConnectionFactory
import javax.jms.Queue
import javax.jms.Session

class NodeRPCTests {
    private val CORDA_VERSION_REGEX = "\\d+(\\.\\d+)?(-\\w+)?".toRegex()
    private val CORDA_VENDOR = "Corda Open Source"
    private val CORDAPPS = listOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP)
    private val CORDAPP_TYPES = setOf("Contract CorDapp", "Workflow CorDapp")
    private val CORDAPP_CONTRACTS_NAME_REGEX = "corda-finance-contracts-$CORDA_VERSION_REGEX".toRegex()
    private val CORDAPP_WORKFLOWS_NAME_REGEX = "corda-finance-workflows-$CORDA_VERSION_REGEX".toRegex()
    private val CORDAPP_SHORT_NAME = "Corda Finance Demo"
    private val CORDAPP_VENDOR = "R3"
    private val CORDAPP_LICENCE = "Open Source (Apache 2)"
    private val HEXADECIMAL_REGEX = "[0-9a-fA-F]+".toRegex()

    @Test(timeout=300_000)
	fun `run nodeDiagnosticInfo`() {
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = CORDAPPS, extraCordappPackagesToScan = emptyList())) {
            val nodeDiagnosticInfo = startNode().get().rpc.nodeDiagnosticInfo()
            assertTrue(nodeDiagnosticInfo.version.matches(CORDA_VERSION_REGEX))
            assertEquals(PLATFORM_VERSION, nodeDiagnosticInfo.platformVersion)
            assertEquals(CORDA_VENDOR, nodeDiagnosticInfo.vendor)
            nodeDiagnosticInfo.cordapps.forEach { println("${it.shortName} ${it.type}") }
            assertEquals(CORDAPPS.size, nodeDiagnosticInfo.cordapps.size)
            assertEquals(CORDAPP_TYPES, nodeDiagnosticInfo.cordapps.map { it.type }.toSet())
            assertTrue(nodeDiagnosticInfo.cordapps.any { it.name.matches(CORDAPP_CONTRACTS_NAME_REGEX) })
            assertTrue(nodeDiagnosticInfo.cordapps.any { it.name.matches(CORDAPP_WORKFLOWS_NAME_REGEX) })
            val cordappInfo = nodeDiagnosticInfo.cordapps.first()
            assertEquals(CORDAPP_SHORT_NAME, cordappInfo.shortName)
            assertTrue(cordappInfo.version.all { it.isDigit() })
            assertEquals(CORDAPP_VENDOR, cordappInfo.vendor)
            assertEquals(CORDAPP_LICENCE, cordappInfo.licence)
            assertTrue(cordappInfo.minimumPlatformVersion <= PLATFORM_VERSION)
            assertTrue(cordappInfo.targetPlatformVersion <= PLATFORM_VERSION)
            assertTrue(cordappInfo.jarHash.toString().matches(HEXADECIMAL_REGEX))
        }
    }
}

class AMQPRPCTests {
    private val CORDAPPS = listOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP)

    private val observableContext = ObservableContext(
            callSiteMap = null,
            observableMap = createRpcObservableMap(),
            hardReferenceStore = Collections.synchronizedSet(mutableSetOf<Observable<*>>())
    )

    @Test(timeout=300_000)
    fun `RPC over AMQP`() {
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = CORDAPPS, extraCordappPackagesToScan = emptyList())) {
            val node = startNode().get();
            var connection: Connection? = null
            val connectionFactory: ConnectionFactory = JmsConnectionFactory("amqp://${node.rpcAddress}")

            try {
                connection = connectionFactory.createConnection()

                // Step 2. Create a session
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

                val sendQueue = session.createQueue("exampleQueue")
                val sender = session.createProducer(sendQueue)
                val message = session.createBytesMessage()

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



                sender.send(session.createBytesMessage("Hello world "))
                connection.start()

                val receiveQueue = session.createQueue("")
                val consumer: MessageConsumer = session.createConsumer(receiveQueue)

                // Step 7. receive the simple message
                val m = consumer.receive(5000) as BytesMessage
            } finally {
                if (connection != null) {
                    // Step 9. close the connection
                    connection.close()
                }
            }
        }
    }
}