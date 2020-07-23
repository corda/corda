package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.KilledFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.node.services.statemachine.Checkpoint
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.util.concurrent.Semaphore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowIsKilledTest {

    private companion object {
        const val EXCEPTION_MESSAGE = "Goodbye, cruel world!"
    }

    @Test(timeout = 300_000)
    fun `manually handle the isKilled check`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.let { rpc ->
                val handle = rpc.startFlow(::AFlowThatWantsToDie)
                AFlowThatWantsToDie.lockA.acquire()
                rpc.killFlow(handle.id)
                AFlowThatWantsToDie.lockB.release()
                assertThatExceptionOfType(KilledFlowException::class.java)
                    .isThrownBy { handle.returnValue.getOrThrow(1.minutes) }
                    .withMessage(EXCEPTION_MESSAGE)
                assertEquals(11, AFlowThatWantsToDie.position)
                val checkpoints = rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, checkpoints)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `manually handled killed flows propagate error to counter parties`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob, charlie) = listOf(ALICE_NAME, BOB_NAME, CHARLIE_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            alice.rpc.let { rpc ->
                val handle = rpc.startFlow(
                    ::AFlowThatWantsToDieAndKillsItsFriends,
                    listOf(bob.nodeInfo.singleIdentity(), charlie.nodeInfo.singleIdentity())
                )
                AFlowThatWantsToDieAndKillsItsFriends.lockA.acquire()
                AFlowThatWantsToDieAndKillsItsFriendsResponder.locks.forEach { it.value.acquire() }
                rpc.killFlow(handle.id)
                AFlowThatWantsToDieAndKillsItsFriends.lockB.release()
                assertThatExceptionOfType(KilledFlowException::class.java)
                    .isThrownBy { handle.returnValue.getOrThrow(1.minutes) }
                    .withMessage(EXCEPTION_MESSAGE)
                AFlowThatWantsToDieAndKillsItsFriendsResponder.locks.forEach { it.value.acquire() }
                assertEquals(11, AFlowThatWantsToDieAndKillsItsFriends.position)
                assertTrue(AFlowThatWantsToDieAndKillsItsFriendsResponder.receivedKilledExceptions[BOB_NAME]!!)
                assertTrue(AFlowThatWantsToDieAndKillsItsFriendsResponder.receivedKilledExceptions[CHARLIE_NAME]!!)
                assertEquals(1, alice.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds))
                assertEquals(2, bob.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds))
                assertEquals(1, bob.rpc.startFlow(::GetNumberOfFailedCheckpointsFlow).returnValue.getOrThrow(20.seconds))
            }
        }
    }

    @Test(timeout = 300_000)
    fun `a manually killed initiated flow will propagate the killed error to the initiator and its counter parties`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()

            val handle = alice.rpc.startFlow(
                ::AFlowThatGetsMurderedByItsFriend,
                bob.nodeInfo.singleIdentity()
            )

            AFlowThatGetsMurderedByItsFriendResponder.lockA.acquire()

            val initiatedFlowId = AFlowThatGetsMurderedByItsFriendResponder.flowId!!

            bob.rpc.killFlow(initiatedFlowId)

            AFlowThatGetsMurderedByItsFriendResponder.lockB.release()

            assertFailsWith<UnexpectedFlowEndException> {
                handle.returnValue.getOrThrow(1.minutes)
            }
            assertEquals(11, AFlowThatGetsMurderedByItsFriendResponder.position)
            assertEquals(2, alice.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds))
            assertEquals(1, alice.rpc.startFlow(::GetNumberOfFailedCheckpointsFlow).returnValue.getOrThrow(20.seconds))
            assertEquals(1, bob.rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds))
        }
    }

    @Test(timeout = 300_000)
    fun `manually handle killed flows using checkFlowIsNotKilled`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.let { rpc ->
                val handle = rpc.startFlow(::AFlowThatChecksIfItWantsToDie)
                AFlowThatChecksIfItWantsToDie.lockA.acquire()
                rpc.killFlow(handle.id)
                AFlowThatChecksIfItWantsToDie.lockB.release()
                assertThatExceptionOfType(KilledFlowException::class.java)
                    .isThrownBy { handle.returnValue.getOrThrow(1.minutes) }
                    .withMessageNotContaining(EXCEPTION_MESSAGE)
                assertEquals(11, AFlowThatChecksIfItWantsToDie.position)
                val checkpoints = rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, checkpoints)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `manually handle killed flows using checkFlowIsNotKilled with lazy message`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.let { rpc ->
                val handle = rpc.startFlow(::AFlowThatChecksIfItWantsToDieAndLeavesANote)
                AFlowThatChecksIfItWantsToDieAndLeavesANote.lockA.acquire()
                rpc.killFlow(handle.id)
                AFlowThatChecksIfItWantsToDieAndLeavesANote.lockB.release()
                assertThatExceptionOfType(KilledFlowException::class.java)
                    .isThrownBy { handle.returnValue.getOrThrow(1.minutes) }
                    .withMessage(EXCEPTION_MESSAGE)
                assertEquals(11, AFlowThatChecksIfItWantsToDieAndLeavesANote.position)
                val checkpoints = rpc.startFlow(::GetNumberOfCheckpointsFlow).returnValue.getOrThrow(20.seconds)
                assertEquals(1, checkpoints)
            }
        }
    }

    @StartableByRPC
    class AFlowThatWantsToDie : FlowLogic<Unit>() {

        companion object {
            val lockA = Semaphore(0)
            val lockB = Semaphore(0)

            var position = 0
        }

        @Suspendable
        override fun call() {
            for (i in 0..100) {
                position = i
                logger.info("i = $i")
                if (isKilled) {
                    throw KilledFlowException(runId, EXCEPTION_MESSAGE)
                }

                if (i == 10) {
                    lockA.release()
                    lockB.acquire()
                }
            }
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class AFlowThatWantsToDieAndKillsItsFriends(private val parties: List<Party>) : FlowLogic<Unit>() {

        companion object {
            val lockA = Semaphore(0)
            val lockB = Semaphore(0)
            var isKilled = false
            var position = 0
        }

        @Suspendable
        override fun call() {
            val sessionOne = initiateFlow(parties[0])
            val sessionTwo = initiateFlow(parties[1])
            // trigger sessions with 2 counter parties
            sessionOne.sendAndReceive<String>("what is up")
            sessionOne.send("what is up 2")
            sessionTwo.sendAndReceive<String>("what is up")
            sessionTwo.send("what is up 2")
            for (i in 0..100) {
                position = i
                logger.info("i = $i")
                if (isKilled) {
                    AFlowThatWantsToDieAndKillsItsFriends.isKilled = true
                    throw KilledFlowException(runId, EXCEPTION_MESSAGE)
                }

                if (i == 10) {
                    lockA.release()
                    lockB.acquire()
                }
            }
        }
    }

    @InitiatedBy(AFlowThatWantsToDieAndKillsItsFriends::class)
    class AFlowThatWantsToDieAndKillsItsFriendsResponder(private val session: FlowSession) : FlowLogic<Unit>() {

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
                require(AFlowThatWantsToDieAndKillsItsFriends.isKilled) {
                    "The initiator must be killed when this exception is received"
                }
                throw e
            }
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class AFlowThatGetsMurderedByItsFriend(private val party: Party) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val sessionOne = initiateFlow(party)
            try {
                sessionOne.sendAndReceive<String>("what is up")
                sessionOne.receive<String>()
            } catch (e: UnexpectedFlowEndException) {
                require(AFlowThatGetsMurderedByItsFriendResponder.isKilled) {
                    "The responder must be killed when this exception is received"
                }
                throw e
            }
        }
    }

    @InitiatedBy(AFlowThatGetsMurderedByItsFriend::class)
    class AFlowThatGetsMurderedByItsFriendResponder(private val session: FlowSession) : FlowLogic<Unit>() {

        companion object {
            val lockA = Semaphore(0)
            val lockB = Semaphore(0)
            var isKilled = false
            var flowId: StateMachineRunId? = null
            var position = 0
        }

        @Suspendable
        override fun call() {
            flowId = runId
            session.receive<String>()
            session.send("hi")
            for (i in 0..100) {
                position = i
                if (isKilled) {
                    AFlowThatGetsMurderedByItsFriendResponder.isKilled = true
                    throw KilledFlowException(runId, EXCEPTION_MESSAGE)
                }

                if (i == 10) {
                    lockA.release()
                    lockB.acquire()
                }
            }
        }
    }

    @StartableByRPC
    class AFlowThatChecksIfItWantsToDie : FlowLogic<Unit>() {

        companion object {
            val lockA = Semaphore(0)
            val lockB = Semaphore(0)

            var position = 0
        }

        @Suspendable
        override fun call() {
            for (i in 0..100) {
                position = i
                logger.info("i = $i")
                checkFlowIsNotKilled()

                if (i == 10) {
                    lockA.release()
                    lockB.acquire()
                }
            }
        }
    }

    @StartableByRPC
    class AFlowThatChecksIfItWantsToDieAndLeavesANote : FlowLogic<Unit>() {

        companion object {
            val lockA = Semaphore(0)
            val lockB = Semaphore(0)

            var position = 0
        }

        @Suspendable
        override fun call() {
            for (i in 0..100) {
                position = i
                logger.info("i = $i")
                checkFlowIsNotKilled { EXCEPTION_MESSAGE }

                if (i == 10) {
                    lockA.release()
                    lockB.acquire()
                }
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

    @StartableByRPC
    class GetNumberOfFailedCheckpointsFlow : FlowLogic<Long>() {
        override fun call(): Long {
            return serviceHub.jdbcSession()
                .prepareStatement("select count(*) from node_checkpoints where status = ${Checkpoint.FlowStatus.FAILED.ordinal}")
                .use { ps ->
                    ps.executeQuery().use { rs ->
                        rs.next()
                        rs.getLong(1)
                    }
                }
        }
    }
}