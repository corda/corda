package net.corda.node.modes.draining

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.RPCException
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.User
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import rx.subjects.AsyncSubject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class P2PFlowsDrainingModeTest {

    private val portAllocation = PortAllocation.Incremental(10000)
    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val users = listOf(user)

    private var executor: ScheduledExecutorService? = null

    companion object {
        private val logger = loggerFor<P2PFlowsDrainingModeTest>()
    }

    @Before
    fun setup() {
        executor = Executors.newSingleThreadScheduledExecutor()
    }

    @After
    fun cleanUp() {
        executor!!.shutdown()
    }

    @Test
    fun `flows draining mode suspends consumption of initial session messages`() {
        driver(DriverParameters(startNodesInProcess = false, portAllocation = portAllocation, notarySpecs = emptyList())) {
            val initiatedNode = startNode(providedName = ALICE_NAME).getOrThrow()
            val initiating = startNode(providedName = BOB_NAME, rpcUsers = users).getOrThrow().rpc
            val counterParty = initiatedNode.nodeInfo.singleIdentity()
            val initiated = initiatedNode.rpc

            initiated.setFlowsDrainingModeEnabled(true)

            var shouldFail = true
            initiating.apply {
                val flow = startFlow(::InitiateSessionFlow, counterParty)
                // this should be really fast, for the flow has already started, so 5 seconds should never be a problem
                executor!!.schedule({
                    logger.info("Now disabling flows draining mode for $counterParty.")
                    shouldFail = false
                    initiated.setFlowsDrainingModeEnabled(false)
                }, 5, TimeUnit.SECONDS)
                flow.returnValue.map { result ->
                    if (shouldFail) {
                        fail("Shouldn't happen until flows draining mode is switched off.")
                    } else {
                        assertThat(result).isEqualTo("Hi there answer")
                    }
                }.getOrThrow()
            }
        }
    }

    @Test
    fun `terminate node waiting for pending flows`() {
        driver(DriverParameters(startNodesInProcess = true, portAllocation = portAllocation, notarySpecs = emptyList())) {
            val nodeA = startNode(providedName = ALICE_NAME, rpcUsers = users).getOrThrow()
            val nodeB = startNode(providedName = BOB_NAME, rpcUsers = users).getOrThrow()
            var successful = false
            val latch = CountDownLatch(1)
            nodeB.rpc.setFlowsDrainingModeEnabled(true)
            IntRange(1, 10).forEach { nodeA.rpc.startFlow(::InitiateSessionFlow, nodeB.nodeInfo.chooseIdentity()) }

            nodeA.waitForShutdown().doOnError { error ->
                error.printStackTrace()
                successful = false
            }.doOnCompleted { successful = true }.doAfterTerminate { latch.countDown() }.subscribe()

            nodeA.rpc.terminate(true)
            nodeB.rpc.setFlowsDrainingModeEnabled(false)

            latch.await()

            assertThat(successful).isTrue()
        }
    }

    // TODO sollecitom come up with a nice way of showing that switching off draining mode cancels terminate(true) [obviously it could be tested with timeouts]
    @Test
    fun `terminate resets persistent draining mode property when waiting for pending flows`() {
        driver(DriverParameters(startNodesInProcess = true, portAllocation = portAllocation, notarySpecs = emptyList())) {
            val nodeA = startNode(providedName = ALICE_NAME, rpcUsers = users).getOrThrow()
            var successful = false
            val latch = CountDownLatch(1)

            // This is useless, as `terminate(true)` sets draining mode anyway, but it's here to ensure that it removes the persistent value anyway.
            nodeA.rpc.setFlowsDrainingModeEnabled(true)
            nodeA.rpc.waitForShutdown().doOnError { error ->
                error.printStackTrace()
                successful = false
                latch.countDown()
            }.doOnCompleted {
                nodeA.stop()
                val nodeARestarted = startNode(providedName = ALICE_NAME, rpcUsers = users).getOrThrow()
                successful = !nodeARestarted.rpc.isFlowsDrainingModeEnabled()
                latch.countDown()
            }.subscribe()

            nodeA.rpc.terminate(true)

            latch.await()

            assertThat(successful).isTrue()
        }
    }
}

// TODO sollecitom make it available to all driver-based tests, or even to NetworkMap based ones.
private fun NodeHandle.waitForShutdown(): Observable<Unit> {

    return rpc.waitForShutdown().doAfterTerminate(::stop)
}

// TODO sollecitom make it available to all driver-based tests, or even to NetworkMap based ones.
private fun CordaRPCOps.waitForShutdown(): Observable<Unit> {

    val completable = AsyncSubject.create<Unit>()
    stateMachinesFeed().updates.subscribe({ _ -> }, { error ->
        // TODO sollecitom create an RPCException sub-type specific for connection failures. Then, replace the check with an `is` check.
        if (error is RPCException && error.message == "Connection failure detected.") {
            completable.onCompleted()
        } else {
            completable.onError(error)
        }
    })
    return completable
}

@StartableByRPC
@InitiatingFlow
class InitiateSessionFlow(private val counterParty: Party) : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {

        val session = initiateFlow(counterParty)
        session.send("Hi there")
        return session.receive<String>().unwrap { it }
    }
}

@InitiatedBy(InitiateSessionFlow::class)
class InitiatedFlow(private val initiatingSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        val message = initiatingSession.receive<String>().unwrap { it }
        initiatingSession.send("$message answer")
    }
}