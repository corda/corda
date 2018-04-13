package net.corda.services.messaging

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.randomOrNull
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.ReceivedMessage
import net.corda.node.services.messaging.send
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.InProcess
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.internalServices
import net.corda.testing.node.ClusterSpec
import net.corda.testing.node.NotarySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class P2PMessagingTest {
    private companion object {
        val DISTRIBUTED_SERVICE_NAME = CordaX500Name("DistributedService", "London", "GB")
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
                alice.internalServices.networkService.getAddressOfParty(getPartyInfo(notaryParty)!!)
            }

            val responseMessage = "response"

            val crashingNodes = simulateCrashingNodes(distributedServiceNodes, responseMessage)

            // Send a single request with retry
            val responseFuture = alice.receiveFrom(serviceAddress, retryId = 0)
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
                alice.internalServices.networkService.getAddressOfParty(getPartyInfo(notaryParty)!!)
            }

            val responseMessage = "response"

            val crashingNodes = simulateCrashingNodes(distributedServiceNodes, responseMessage)

            // Send a single request with retry
            alice.receiveFrom(serviceAddress, retryId = 0)

            // Wait until the first request is received
            crashingNodes.firstRequestReceived.await()
            // Stop alice's node after we ensured that the first request was delivered and ignored.
            alice.stop()
            val numberOfRequestsReceived = crashingNodes.requestsReceived.get()
            assertThat(numberOfRequestsReceived).isGreaterThanOrEqualTo(1)

            crashingNodes.ignoreRequests = false

            // Restart the node and expect a response
            val aliceRestarted = startAlice()

            val responseFuture = openFuture<Any>()
            aliceRestarted.internalServices.networkService.runOnNextMessage("test.response") {
                responseFuture.set(it.data.deserialize())
            }
            val response = responseFuture.getOrThrow()

            assertThat(crashingNodes.requestsReceived.get()).isGreaterThan(numberOfRequestsReceived)
            assertThat(response).isEqualTo(responseMessage)
        }
    }


    private fun startDriverWithDistributedService(dsl: DriverDSL.(List<InProcess>) -> Unit) {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = listOf(NotarySpec(DISTRIBUTED_SERVICE_NAME, cluster = ClusterSpec.Raft(clusterSize = 2))))) {
            dsl(defaultNotaryHandle.nodeHandles.getOrThrow().map { (it as InProcess) })
        }
    }

    private fun DriverDSL.startAlice(): InProcess {
        return startNode(providedName = ALICE_NAME, customOverrides = mapOf("messageRedeliveryDelaySeconds" to 1))
                .map { (it as InProcess) }
                .getOrThrow()
    }

    data class CrashingNodes(
            val firstRequestReceived: CountDownLatch,
            val requestsReceived: AtomicInteger,
            var ignoreRequests: Boolean
    )

    /**
     * Sets up the [distributedServiceNodes] to respond to "test.request" requests. All nodes will receive requests and
     * either ignore them or respond to "test.response", depending on the value of [CrashingNodes.ignoreRequests],
     * initially set to true. This may be used to simulate scenarios where nodes receive request messages but crash
     * before sending back a response.
     */
    private fun simulateCrashingNodes(distributedServiceNodes: List<InProcess>, responseMessage: String): CrashingNodes {
        val crashingNodes = CrashingNodes(
                requestsReceived = AtomicInteger(0),
                firstRequestReceived = CountDownLatch(1),
                ignoreRequests = true
        )

        distributedServiceNodes.forEach {
            val nodeName = it.services.myInfo.legalIdentitiesAndCerts.first().name
            it.internalServices.networkService.addMessageHandler("test.request") { netMessage, _, handler ->
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
                    val response = it.internalServices.networkService.createMessage("test.response", responseMessage.serialize().bytes)
                    it.internalServices.networkService.send(response, request.replyTo)
                }
                handler.afterDatabaseTransaction()
            }
        }
        return crashingNodes
    }

    private fun assertAllNodesAreUsed(participatingServiceNodes: List<InProcess>, serviceName: CordaX500Name, originatingNode: InProcess) {
        // Setup each node in the distributed service to return back it's NodeInfo so that we can know which node is being used
        participatingServiceNodes.forEach { node ->
            node.respondWith(node.services.myInfo)
        }
        val serviceAddress = originatingNode.services.networkMapCache.run {
            originatingNode.internalServices.networkService.getAddressOfParty(getPartyInfo(getNotary(serviceName)!!)!!)
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
        assertThat(participatingNodes).containsOnlyElementsOf(participatingServiceNodes.map { it.services.myInfo })
    }
    
    private fun InProcess.respondWith(message: Any) {
        internalServices.networkService.addMessageHandler("test.request") { netMessage, _, handler ->
            val request = netMessage.data.deserialize<TestRequest>()
            val response = internalServices.networkService.createMessage("test.response", message.serialize().bytes)
            internalServices.networkService.send(response, request.replyTo)
            handler.afterDatabaseTransaction()
        }
    }

    private fun InProcess.receiveFrom(target: MessageRecipients, retryId: Long? = null): CordaFuture<Any> {
        val response = openFuture<Any>()
        internalServices.networkService.runOnNextMessage("test.response") { netMessage ->
            response.set(netMessage.data.deserialize())
        }
        internalServices.networkService.send("test.request", TestRequest(replyTo = internalServices.networkService.myAddress), target, retryId = retryId)
        return response
    }

    /**
     * Registers a handler for the given topic and session that runs the given callback with the message and then removes
     * itself. This is useful for one-shot handlers that aren't supposed to stick around permanently. Note that this callback
     * doesn't take the registration object, unlike the callback to [MessagingService.addMessageHandler].
     *
     * @param topic identifier for the topic and session to listen for messages arriving on.
     */
    inline fun MessagingService.runOnNextMessage(topic: String, crossinline callback: (ReceivedMessage) -> Unit) {
        val consumed = AtomicBoolean()
        addMessageHandler(topic) { msg, reg, handler ->
            removeMessageHandler(reg)
            check(!consumed.getAndSet(true)) { "Called more than once" }
            check(msg.topic == topic) { "Topic/session mismatch: ${msg.topic} vs $topic" }
            callback(msg)
            handler.afterDatabaseTransaction()
        }
    }

    @CordaSerializable
    private data class TestRequest(val replyTo: SingleMessageRecipient)
}
