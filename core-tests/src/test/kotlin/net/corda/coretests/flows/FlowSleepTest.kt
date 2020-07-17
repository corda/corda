package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertTrue

class FlowSleepTest {

    @Test(timeout = 300_000)
    fun `flow can sleep`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val (start, finish) = alice.rpc.startFlow(::SleepyFlow).returnValue.getOrThrow(1.minutes)
            val difference = Duration.between(start, finish)
            assertTrue(difference >= 5.seconds)
            assertTrue(difference < 7.seconds)
        }
    }

    @Test(timeout = 300_000)
    fun `flow can sleep multiple times`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val (start, middle, finish) = alice.rpc.startFlow(::AnotherSleepyFlow).returnValue.getOrThrow(1.minutes)
            val differenceBetweenStartAndMiddle = Duration.between(start, middle)
            val differenceBetweenMiddleAndFinish = Duration.between(middle, finish)
            assertTrue(differenceBetweenStartAndMiddle >= 5.seconds)
            assertTrue(differenceBetweenStartAndMiddle < 7.seconds)
            assertTrue(differenceBetweenMiddleAndFinish >= 10.seconds)
            assertTrue(differenceBetweenMiddleAndFinish < 12.seconds)
        }
    }

    @Test(timeout = 300_000)
    fun `flow can sleep and perform other suspending functions`() {
        // ensures that events received while the flow is sleeping are not processed
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            val (start, finish) = alice.rpc.startFlow(
                ::SleepAndInteractWithPartyFlow,
                bob.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(1.minutes)
            val difference = Duration.between(start, finish)
            assertTrue(difference >= 5.seconds)
            assertTrue(difference < 7.seconds)
        }
    }

    @StartableByRPC
    class SleepyFlow : FlowLogic<Pair<Instant, Instant>>() {

        @Suspendable
        override fun call(): Pair<Instant, Instant> {
            val start = Instant.now()
            sleep(5.seconds)
            return start to Instant.now()
        }
    }

    @StartableByRPC
    class AnotherSleepyFlow : FlowLogic<Triple<Instant, Instant, Instant>>() {

        @Suspendable
        override fun call(): Triple<Instant, Instant, Instant> {
            val start = Instant.now()
            sleep(5.seconds)
            val middle = Instant.now()
            sleep(10.seconds)
            return Triple(start, middle, Instant.now())
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class SleepAndInteractWithPartyFlow(private val party: Party) : FlowLogic<Pair<Instant, Instant>>() {

        @Suspendable
        override fun call(): Pair<Instant, Instant> {
            subFlow(PingPongFlow(party))
            val start = Instant.now()
            sleep(5.seconds)
            val finish = Instant.now()
            val session = initiateFlow(party)
            session.sendAndReceive<String>("hi")
            session.sendAndReceive<String>("hi")
            subFlow(PingPongFlow(party))
            return start to finish
        }
    }

    @InitiatedBy(SleepAndInteractWithPartyFlow::class)
    class SleepAndInteractWithPartyResponder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            session.receive<String>().unwrap { it }
            session.send("go away")
            session.receive<String>().unwrap { it }
            session.send("go away")
        }
    }

    @InitiatingFlow
    class PingPongFlow(val party: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(party)
            session.sendAndReceive<String>("ping pong").unwrap { it }
        }
    }

    @InitiatedBy(PingPongFlow::class)
    class PingPongResponder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            session.receive<String>().unwrap { it }
            session.send("I got you bro")
        }
    }
}