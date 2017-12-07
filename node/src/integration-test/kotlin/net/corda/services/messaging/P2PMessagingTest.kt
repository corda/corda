package net.corda.services.messaging

import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.randomOrNull
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.services.messaging.*
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.testing.ALICE
import net.corda.testing.chooseIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.ClusterSpec
import net.corda.testing.node.NotarySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class P2PMessagingTest {
    private companion object {
        val DISTRIBUTED_SERVICE_NAME = CordaX500Name(RaftValidatingNotaryService.id, "DistributedService", "London", "GB")
    }

    @Test
    fun `communicating with a distributed service which we're part of`() {
        startDriverWithDistributedService { distributedService ->
            assertAllNodesAreUsed(distributedService, DISTRIBUTED_SERVICE_NAME, distributedService[0])
        }
    }

    @Test
    fun `distributed service requests are retried if one of the nodes in the cluster goes down without sending a response`() {
        startDriverWithDistributedService { distributedServiceNodes ->
            val alice = startAlice()
            val serviceAddress = alice.services.networkMapCache.run {
                val notaryParty = notaryIdentities.randomOrNull()!!
                alice.network.getAddressOfParty(getPartyInfo(notaryParty)!!)
            }

            val dummyTopic = "dummy.topic"
            val responseMessage = "response"

            val crashingNodes = simulateCrashingNodes(distributedServiceNodes, dummyTopic, responseMessage)

            // Send a single request with retry
            val responseFuture = with(alice.network) {
                val request = TestRequest(replyTo = myAddress)
                val responseFuture = onNext<Any>(dummyTopic, request.sessionID)
                val msg = createMessage(TopicSession(dummyTopic), data = request.serialize().bytes)
                send(msg, serviceAddress, retryId = request.sessionID)
                responseFuture
            }
            crashingNodes.firstRequestReceived.await(5, TimeUnit.SECONDS)
            // The request wasn't successful.
            assertThat(responseFuture.isDone).isFalse()
            crashingNodes.ignoreRequests = false

            // The retry should be successful.
            val response = responseFuture.getOrThrow(10.seconds)
            assertThat(response).isEqualTo(responseMessage)
        }
    }

    @Test
    fun `distributed service request retries are persisted across client node restarts`() {
        startDriverWithDistributedService { distributedServiceNodes ->
            val alice = startAlice()
            val serviceAddress = alice.services.networkMapCache.run {
                val notaryParty = notaryIdentities.randomOrNull()!!
                alice.network.getAddressOfParty(getPartyInfo(notaryParty)!!)
            }

            val dummyTopic = "dummy.topic"
            val responseMessage = "response"

            val crashingNodes = simulateCrashingNodes(distributedServiceNodes, dummyTopic, responseMessage)

            val sessionId = random63BitValue()

            // Send a single request with retry
            with(alice.network) {
                val request = TestRequest(sessionId, myAddress)
                val msg = createMessage(TopicSession(dummyTopic), data = request.serialize().bytes)
                send(msg, serviceAddress, retryId = request.sessionID)
            }

            // Wait until the first request is received
            crashingNodes.firstRequestReceived.await(5, TimeUnit.SECONDS)
            // Stop alice's node after we ensured that the first request was delivered and ignored.
            alice.dispose()
            val numberOfRequestsReceived = crashingNodes.requestsReceived.get()
            assertThat(numberOfRequestsReceived).isGreaterThanOrEqualTo(1)

            crashingNodes.ignoreRequests = false

            // Restart the node and expect a response
            val aliceRestarted = startAlice()
            val response = aliceRestarted.network.onNext<Any>(dummyTopic, sessionId).getOrThrow(5.seconds)

            assertThat(crashingNodes.requestsReceived.get()).isGreaterThan(numberOfRequestsReceived)
            assertThat(response).isEqualTo(responseMessage)
        }
    }

    private fun startDriverWithDistributedService(dsl: DriverDSL.(List<StartedNode<Node>>) -> Unit) {
        driver(startNodesInProcess = true, notarySpecs = listOf(NotarySpec(DISTRIBUTED_SERVICE_NAME, cluster = ClusterSpec.Raft(clusterSize = 2)))) {
            dsl(defaultNotaryHandle.nodeHandles.getOrThrow().map { (it as NodeHandle.InProcess).node })
        }
    }

    private fun DriverDSL.startAlice(): StartedNode<Node> {
        return startNode(providedName = ALICE.name, customOverrides = mapOf("messageRedeliveryDelaySeconds" to 1))
                .map { (it as NodeHandle.InProcess).node }
                .getOrThrow()
    }

    data class CrashingNodes(
            val firstRequestReceived: CountDownLatch,
            val requestsReceived: AtomicInteger,
            var ignoreRequests: Boolean
    )

    /**
     * Sets up the [distributedServiceNodes] to respond to [dummyTopic] requests. All nodes will receive requests and
     * either ignore them or respond, depending on the value of [CrashingNodes.ignoreRequests], initially set to true.
     * This may be used to simulate scenarios where nodes receive request messages but crash before sending back a response.
     */
    private fun simulateCrashingNodes(distributedServiceNodes: List<StartedNode<*>>, dummyTopic: String, responseMessage: String): CrashingNodes {
        val crashingNodes = CrashingNodes(
                requestsReceived = AtomicInteger(0),
                firstRequestReceived = CountDownLatch(1),
                ignoreRequests = true
        )

        distributedServiceNodes.forEach {
            val nodeName = it.info.chooseIdentity().name
            it.network.addMessageHandler(dummyTopic) { netMessage, _ ->
                crashingNodes.requestsReceived.incrementAndGet()
                crashingNodes.firstRequestReceived.countDown()
                // The node which receives the first request will ignore all requests
                print("$nodeName: Received request - ")
                if (crashingNodes.ignoreRequests) {
                    println("ignoring")
                    // Requests are ignored to simulate a service node crashing before sending back a response.
                    // A retry by the client will result in the message being redelivered to another node in the service cluster.
                } else {
                    println("sending response")
                    val request = netMessage.data.deserialize<TestRequest>()
                    val response = it.network.createMessage(dummyTopic, request.sessionID, responseMessage.serialize().bytes)
                    it.network.send(response, request.replyTo)
                }
            }
        }
        return crashingNodes
    }

    private fun assertAllNodesAreUsed(participatingServiceNodes: List<StartedNode<*>>, serviceName: CordaX500Name, originatingNode: StartedNode<*>) {
        // Setup each node in the distributed service to return back it's NodeInfo so that we can know which node is being used
        participatingServiceNodes.forEach { node ->
            node.respondWith(node.info)
        }
        val serviceAddress = originatingNode.services.networkMapCache.run {
            originatingNode.network.getAddressOfParty(getPartyInfo(getNotary(serviceName)!!)!!)
        }
        val participatingNodes = HashSet<Any>()
        // Try several times so that we can be fairly sure that any node not participating is not due to Artemis' selection
        // strategy. 3 attempts for each node seems to be sufficient.
        // This is not testing the distribution of the requests - DistributedServiceTests already does that
        for (it in 1..participatingServiceNodes.size * 3) {
            participatingNodes += originatingNode.receiveFrom(serviceAddress).getOrThrow(10.seconds)
            if (participatingNodes.size == participatingServiceNodes.size) {
                break
            }
        }
        assertThat(participatingNodes).containsOnlyElementsOf(participatingServiceNodes.map(StartedNode<*>::info))
    }

    private fun StartedNode<*>.respondWith(message: Any) {
        network.addMessageHandler(javaClass.name) { netMessage, _ ->
            val request = netMessage.data.deserialize<TestRequest>()
            val response = network.createMessage(javaClass.name, request.sessionID, message.serialize().bytes)
            network.send(response, request.replyTo)
        }
    }

    private fun StartedNode<*>.receiveFrom(target: MessageRecipients): CordaFuture<Any> {
        val request = TestRequest(replyTo = network.myAddress)
        return network.sendRequest(javaClass.name, request, target)
    }

    @CordaSerializable
    private data class TestRequest(override val sessionID: Long = random63BitValue(),
                                   override val replyTo: SingleMessageRecipient) : ServiceRequestMessage
}
