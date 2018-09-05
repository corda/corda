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
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import rx.Subscription
import rx.schedulers.Schedulers
import rx.subjects.AsyncSubject
import rx.subjects.PublishSubject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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

    //    @Test
    //    fun `clean shutdown by draining`() {
    //        driver(DriverParameters(startNodesInProcess = true, portAllocation = portAllocation, notarySpecs = emptyList())) {
    //            val nodeA = startNode(providedName = ALICE_NAME, rpcUsers = users).getOrThrow()
    //            val nodeB = startNode(providedName = BOB_NAME, rpcUsers = users).getOrThrow()
    //            var successful = false
    //            val latch = CountDownLatch(1)
    //            nodeB.rpc.setFlowsDrainingModeEnabled(true)
    //            IntRange(1, 10).forEach { nodeA.rpc.startFlow(::InitiateSessionFlow, nodeB.nodeInfo.chooseIdentity()) }
    //
    //            nodeA.rpc.shutdown(true)
    //            nodeA.rpc.drainAndShutdown()
    //                    .doOnError { error ->
    //                        error.printStackTrace()
    //                        successful = false
    //                    }
    //                    .doOnCompleted { successful = true }
    //                    .doAfterTerminate { latch.countDown() }
    //                    .subscribe()
    //            nodeB.rpc.setFlowsDrainingModeEnabled(false)
    //            latch.await()
    //
    //            assertThat(successful).isTrue()
    //        }
    //    }

    @Test
    fun blah() {
        driver(DriverParameters(startNodesInProcess = true, portAllocation = portAllocation, notarySpecs = emptyList())) {
            val nodeA = startNode(providedName = ALICE_NAME, rpcUsers = users).getOrThrow()
            val latch = CountDownLatch(1)

            nodeA.rpc.waitForShutdown().doAfterTerminate(latch::countDown).observeOn(Schedulers.io()).subscribe({ }, { error ->  })
            nodeA.rpc.terminate()
            latch.await()
            logger.info("Worked!")
        }
    }
}

private fun CordaRPCOps.waitForShutdown(): Observable<Unit> {

    val completable = AsyncSubject.create<Unit>()
    stateMachinesFeed().updates.observeOn(Schedulers.io()).subscribe({ _ -> }, { error ->
        if (error is RPCException) {
            completable.onCompleted()
        } else {
            throw error
        }
    })
    return completable
}

//private fun NodeHandle.waitForShutdown(): Observable<Unit> {
//
//    val nodeIsShut: PublishSubject<Unit> = PublishSubject.create()
//    val maxCount = 20
//    var count = 0
//    CloseableExecutor(Executors.newSingleThreadScheduledExecutor()).use { scheduler ->
//
//        val task = scheduler.scheduleAtFixedRate({
//            try {
//                println("Checking whether node is still running...")
//                start(rpcUser.username, rpcUser.password).use {
//                    println("... node is still running.")
//                    if (count == maxCount) {
//                        nodeIsShut.onError(AssertionError("Node does not get shutdown by RPC"))
//                    }
//                    count++
//                }
//            } catch (e: RPCException) {
//                println("... node is not running.")
//                nodeIsShut.onCompleted()
//            } catch (e: ActiveMQSecurityException) {
//                // nothing here - this happens if trying to connect before the node is started
//            } catch (e: Throwable) {
//                nodeIsShut.onError(e)
//            }
//        }, 1, 1, TimeUnit.SECONDS)
//    }
//    return nodeIsShut
//}

private class CloseableExecutor(private val delegate: ScheduledExecutorService) : AutoCloseable, ScheduledExecutorService by delegate {
    override fun close() {
        delegate.shutdown()
    }
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