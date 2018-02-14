package net.corda.node.modes.draining

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.chooseIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.fail

class P2PFlowsDrainingModeTest {

    private val portAllocation = PortAllocation.Incremental(10000)
    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val users = listOf(user)

    private var executor: ExecutorService? = null

    companion object {
        private val logger = loggerFor<P2PFlowsDrainingModeTest>()
    }

    @Before
    fun setup() {
        executor = Executors.newSingleThreadExecutor()
    }

    @After
    fun cleanUp() {
        executor!!.shutdown()
    }

    @Test
    fun `flows draining mode suspends consumption of initial session messages`() {

        driver(DriverParameters(isDebug = true, startNodesInProcess = false, portAllocation = portAllocation)) {
            val initiatedNode = startNode().getOrThrow()
            val initiating = startNode(rpcUsers = users).getOrThrow().rpc
            val counterParty = initiatedNode.nodeInfo.chooseIdentity()
            val initiated = initiatedNode.rpc

            initiated.setFlowsDrainingModeEnabled(true)

            var shouldFail = true
            initiating.apply {
                val flow = startFlow(::InitiateSessionFlow, counterParty)
                // this should be really fast, for the flow has already started, so 5 seconds should never be a problem
                executor!!.submit({
                    logger.info("Now disabling flows draining mode for $counterParty.")
                    shouldFail = false
                    initiated.setFlowsDrainingModeEnabled(false)
                })
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
}