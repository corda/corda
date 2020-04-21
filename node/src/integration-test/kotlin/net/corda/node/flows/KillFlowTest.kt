package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.KilledFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Semaphore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KillFlowTest {

    @Before
    fun setup() {
        Configurator.setLevel("net.corda.node.services.statemachine", Level.DEBUG)
    }

    @Test(timeout = 300_000)
    fun `a killed flow will end when it reaches the next suspension point`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.let { rpc ->
                val handle = rpc.startFlow(::AFlowThatGetsMurderedWhenItTriesToSuspend)
                AFlowThatGetsMurderedWhenItTriesToSuspend.lockA.acquire()
                rpc.killFlow(handle.id)
                AFlowThatGetsMurderedWhenItTriesToSuspend.lockB.release()
                assertFailsWith<KilledFlowException> {
                    handle.returnValue.getOrThrow(1.minutes)
                }
                val checkpoints = rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, checkpoints)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `a killed flow will propagate the killed error to counter parties when it reaches the next suspension point`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            val charlie = startNode(providedName = CHARLIE_NAME).getOrThrow()
            alice.rpc.let { rpc ->
                val handle = rpc.startFlow(
                    ::AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriends,
                    listOf(bob.nodeInfo.singleIdentity(), charlie.nodeInfo.singleIdentity())
                )
                AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriends.lockA.acquire()
                AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriendsResponder.locks.forEach { it.value.acquire() }
                rpc.killFlow(handle.id)
                AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriends.lockB.release()
                assertFailsWith<KilledFlowException> {
                    handle.returnValue.getOrThrow(1.minutes)
                }
                AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriendsResponder.locks.forEach { it.value.acquire() }
                assertTrue(AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriendsResponder.receivedKilledExceptions[BOB_NAME]!!)
                assertTrue(AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriendsResponder.receivedKilledExceptions[CHARLIE_NAME]!!)
                val aliceCheckpoints = rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, aliceCheckpoints)
                val bobCheckpoints = bob.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, bobCheckpoints)
                val charlieCheckpoints = charlie.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, charlieCheckpoints)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `a killed flow will end if it was suspended`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.let { rpc ->
                val handle = rpc.startFlow(::AFlowThatGetsMurdered, bob.nodeInfo.singleIdentity())
                Thread.sleep(5000)
                rpc.killFlow(handle.id)
                assertFailsWith<KilledFlowException> {
                    handle.returnValue.getOrThrow(1.minutes)
                }
                val checkpoints = rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, checkpoints)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `a killed flow will propagate the killed error to counter parties if it was suspended`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            val charlie = startNode(providedName = CHARLIE_NAME).getOrThrow()
            alice.rpc.let { rpc ->
                val handle = rpc.startFlow(
                    ::AFlowThatGetsMurderedAndSomehowKillsItsFriends,
                    listOf(bob.nodeInfo.singleIdentity(), charlie.nodeInfo.singleIdentity())
                )
                AFlowThatGetsMurderedAndSomehowKillsItsFriendsResponder.locks.forEach {
                    it.value.acquire()
                }
                rpc.killFlow(handle.id)
                assertFailsWith<KilledFlowException> {
                    handle.returnValue.getOrThrow(20.seconds)
                }
                AFlowThatGetsMurderedAndSomehowKillsItsFriendsResponder.locks.forEach {
                    it.value.acquire()
                }
                assertTrue(AFlowThatGetsMurderedAndSomehowKillsItsFriendsResponder.receivedKilledExceptions[BOB_NAME]!!)
                assertTrue(AFlowThatGetsMurderedAndSomehowKillsItsFriendsResponder.receivedKilledExceptions[CHARLIE_NAME]!!)
                val aliceCheckpoints = rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, aliceCheckpoints)
                val bobCheckpoints = bob.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, bobCheckpoints)
                val charlieCheckpoints = charlie.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, charlieCheckpoints)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `a killed initiated flow will propagate the killed error to the initiator and its counter parties`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            val charlie = startNode(providedName = CHARLIE_NAME).getOrThrow()
            val handle = alice.rpc.startFlow(
                ::AFlowThatGetsMurderedByItsFriend,
                listOf(bob.nodeInfo.singleIdentity(), charlie.nodeInfo.singleIdentity())
            )

            AFlowThatGetsMurderedByItsFriendResponder.locks.forEach { it.value.acquire() }

            val initiatedFlowId = AFlowThatGetsMurderedByItsFriendResponder.flowIds[BOB_NAME]!!

            bob.rpc.killFlow(initiatedFlowId)

            assertFailsWith<UnexpectedFlowEndException> {
                handle.returnValue.getOrThrow(1.minutes)
            }
            AFlowThatGetsMurderedByItsFriendResponder.locks[CHARLIE_NAME]!!.acquire()
            assertTrue(AFlowThatGetsMurderedByItsFriend.receivedKilledException)
            assertFalse(AFlowThatGetsMurderedByItsFriendResponder.receivedKilledExceptions[BOB_NAME]!!)
            assertTrue(AFlowThatGetsMurderedByItsFriendResponder.receivedKilledExceptions[CHARLIE_NAME]!!)
            val aliceCheckpoints = alice.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
            assertEquals(1, aliceCheckpoints)
            val bobCheckpoints = bob.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
            assertEquals(1, bobCheckpoints)
            val charlieCheckpoints = charlie.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
            assertEquals(1, charlieCheckpoints)
        }
    }

    @StartableByRPC
    class AFlowThatGetsMurderedWhenItTriesToSuspend : FlowLogic<Unit>() {

        companion object {
            val lockA = Semaphore(0)
            val lockB = Semaphore(0)
        }

        @Suspendable
        override fun call() {
            lockA.release()
            lockB.acquire()
            sleep(1.seconds)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriends(private val parties: List<Party>) : FlowLogic<Unit>() {

        companion object {
            val lockA = Semaphore(0)
            val lockB = Semaphore(0)
        }

        @Suspendable
        override fun call() {
            val sessionOne = initiateFlow(parties[0])
            val sessionTwo = initiateFlow(parties[1])
            // trigger sessions with 2 counter parties
            sessionOne.sendAndReceive<String>("what is up")
            sessionTwo.sendAndReceive<String>("what is up")
            sessionTwo.send("what is up 2")
            lockA.release()
            lockB.acquire()
            sleep(1.seconds)
        }
    }

    @InitiatedBy(AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriends::class)
    class AFlowThatGetsMurderedWhenItTriesToSuspendAndSomehowKillsItsFriendsResponder(private val session: FlowSession) :
        FlowLogic<Unit>() {

        companion object {
            val locks = mapOf(
                BOB_NAME to Semaphore(0),
                CHARLIE_NAME to Semaphore(0)
            )
            var receivedKilledExceptions = mutableMapOf(
                BOB_NAME to false,
                CHARLIE_NAME to false
            )
        }

        @Suspendable
        override fun call() {
            session.receive<String>()
            session.send("hi")
            session.receive<String>()
            locks[ourIdentity.name]!!.release()
            try {
                session.receive<String>()
            } catch (e: UnexpectedFlowEndException) {
                receivedKilledExceptions[ourIdentity.name] = true
                locks[ourIdentity.name]!!.release()
                throw e
            }
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class AFlowThatGetsMurdered(private val party: Party) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            sleep(1.minutes)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class AFlowThatGetsMurderedAndSomehowKillsItsFriends(private val parties: List<Party>) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val sessionOne = initiateFlow(parties[0])
            val sessionTwo = initiateFlow(parties[1])
            // trigger sessions with 2 counter parties
            sessionOne.sendAndReceive<String>("what is up")
            sessionOne.send("what is up 2")
            // why is this second send needed to cause the kill command to propagate to the other side
            // will be exactly the same for normal error propagation
            sessionTwo.sendAndReceive<String>("what is up")
            sessionTwo.send("what is up 2")
            sleep(3.minutes)
        }
    }

    @InitiatedBy(AFlowThatGetsMurderedAndSomehowKillsItsFriends::class)
    class AFlowThatGetsMurderedAndSomehowKillsItsFriendsResponder(private val session: FlowSession) : FlowLogic<Unit>() {

        companion object {
            val locks = mapOf(
                BOB_NAME to Semaphore(0),
                CHARLIE_NAME to Semaphore(0)
            )
            var receivedKilledExceptions = mutableMapOf(
                BOB_NAME to false,
                CHARLIE_NAME to false
            )
        }

        @Suspendable
        override fun call() {
            session.receive<String>()
            session.send("hi")
            session.receive<String>()
            locks[ourIdentity.name]!!.release()
            try {
                session.receive<String>()
            } catch (e: UnexpectedFlowEndException) {
                receivedKilledExceptions[ourIdentity.name] = true
                locks[ourIdentity.name]!!.release()
                throw e
            }
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class AFlowThatGetsMurderedByItsFriend(private val parties: List<Party>) : FlowLogic<Unit>() {

        companion object {
            var receivedKilledException = false
        }

        @Suspendable
        override fun call() {
            val sessionOne = initiateFlow(parties[0])
            val sessionTwo = initiateFlow(parties[1])
            // trigger sessions with 2 counter parties
            sessionOne.sendAndReceive<String>("what is up")
            sessionOne.send("what is up 2")
            // why is this second send needed to cause the kill command to propagate to the other side
            // will be exactly the same for normal error propagation
            sessionTwo.sendAndReceive<String>("what is up")
            sessionTwo.send("what is up 2")
            try {
                sessionOne.receive<String>()
            } catch (e: UnexpectedFlowEndException) {
                logger.info("Received exception in initiating flow")
                receivedKilledException = true
                throw e
            }
        }
    }

    @InitiatedBy(AFlowThatGetsMurderedByItsFriend::class)
    class AFlowThatGetsMurderedByItsFriendResponder(private val session: FlowSession) : FlowLogic<Unit>() {

        companion object {
            val locks = mapOf(
                BOB_NAME to Semaphore(0),
                CHARLIE_NAME to Semaphore(0)
            )
            var receivedKilledExceptions = mutableMapOf(
                BOB_NAME to false,
                CHARLIE_NAME to false
            )
            var flowIds = mutableMapOf<CordaX500Name, StateMachineRunId>()
        }

        @Suspendable
        override fun call() {
            flowIds[ourIdentity.name] = runId
            session.receive<String>()
            session.send("hi")
            session.receive<String>()
            locks[ourIdentity.name]!!.release()
            try {
                session.receive<String>()
            } catch (e: UnexpectedFlowEndException) {
                receivedKilledExceptions[ourIdentity.name] = true
                locks[ourIdentity.name]!!.release()
                throw e
            }
        }
    }

    @StartableByRPC
    class GetNumberOfCheckpointsFlow : FlowLogic<Long>() {
        override fun call(): Long {
            return serviceHub.jdbcSession().prepareStatement("select count(*) from node_checkpoints").use { ps ->
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }
    }
}