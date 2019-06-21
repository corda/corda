package net.corda.node.services.rpc

import net.corda.client.rpc.internal.ReconnectingCordaRPCOps
import net.corda.client.rpc.internal.asReconnecting
import net.corda.core.contracts.Amount
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.OutOfProcessImpl
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This is a slow test!
 */
class RpcReconnectTests {

    companion object {
        private val log = contextLogger()
    }

    private val portAllocator = incrementalPortAllocation(20006)

    /**
     * This test showcases and stress tests the demo [ReconnectingCordaRPCOps].
     *
     * Note that during node failure events can be lost and starting flows can become unreliable.
     * The only available way to retry failed flows is to attempt a "logical retry" which is also showcased.
     *
     * This test runs flows in a loop and in the background kills the node or restarts it.
     * Also the RPC connection is made through a proxy that introduces random latencies and is also periodically killed.
     */
    @Test
    fun `test that the RPC client is able to reconnect and proceed after node failure, restart, or connection reset`() {
        val nrOfFlowsToRun = 150 // Takes around 5 minutes.
        val nodeRunningTime = { Random().nextInt(12000) + 8000 }

        val demoUser = User("demo", "demo", setOf(Permissions.all()))

        val nodePort = portAllocator.nextPort()
        val proxyPort = portAllocator.nextPort()
        val tcpProxy = RandomFailingProxy(serverPort = proxyPort, remotePort = nodePort).start()

        // When this reaches 0 - the test will end.
        val flowsCountdownLatch = CountDownLatch(nrOfFlowsToRun)

        // These are the expected progress steps for the CashIssueAndPayFlow.
        val expectedProgress = listOf(
                "Starting",
                "Issuing cash",
                "Generating transaction",
                "Signing transaction",
                "Finalising transaction",
                "Broadcasting transaction to participants",
                "Paying recipient",
                "Generating anonymous identities",
                "Generating transaction",
                "Signing transaction",
                "Finalising transaction",
                "Requesting signature by notary service",
                "Requesting signature by Notary service",
                "Validating response from Notary service",
                "Broadcasting transaction to participants",
                "Done"
        )

        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS, startNodesInProcess = false, inMemoryDB = false)) {
            fun startBankA() = startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("rpcSettings.address" to "localhost:$nodePort"))

            var (bankA, bankB) = listOf(
                    startBankA(),
                    startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = listOf(demoUser))
            ).transpose().getOrThrow()

            val notary = defaultNotaryIdentity
            val baseAmount = Amount.parseCurrency("0 USD")
            val issuerRef = OpaqueBytes.of(0x01)

            // Create a reconnecting rpc client through the TCP proxy.
            val bankAAddress = bankA.rpcAddress.copy(port = proxyPort)
            // DOCSTART rpcReconnectingRPC
            val bankAReconnectingRpc = ReconnectingCordaRPCOps(bankAAddress, demoUser.username, demoUser.password)
            // DOCEND rpcReconnectingRPC

            // Observe the vault and collect the observations.
            val vaultEvents = Collections.synchronizedList(mutableListOf<Vault.Update<Cash.State>>())
            // DOCSTART rpcReconnectingRPCVaultTracking
            val vaultFeed = bankAReconnectingRpc.vaultTrackByWithPagingSpec(
                    Cash.State::class.java,
                    QueryCriteria.VaultQueryCriteria(),
                    PageSpecification(1, 1))
            val vaultObserverHandle = vaultFeed.updates.subscribe { update: Vault.Update<Cash.State> ->
                log.info("vault update produced ${update.produced.map { it.state.data.amount }} consumed ${update.consumed.map { it.ref }}")
                vaultEvents.add(update)
            }
            // DOCEND rpcReconnectingRPCVaultTracking

            // Observe the stateMachine and collect the observations.
            val stateMachineEvents = Collections.synchronizedList(mutableListOf<StateMachineUpdate>())
            val stateMachineObserverHandle = bankAReconnectingRpc.stateMachinesFeed().updates.subscribe { update ->
                log.info(update.toString())
                stateMachineEvents.add(update)
            }

            // While the flows are running, randomly apply a different failure scenario.
            val nrRestarts = AtomicInteger()
            thread(name = "Node killer") {
                while (true) {
                    if (flowsCountdownLatch.count == 0L) break

                    // Let the node run for a random time interval.
                    nodeRunningTime().also { ms ->
                        log.info("Running node for ${ms / 1000} s.")
                        Thread.sleep(ms.toLong())
                    }

                    if (flowsCountdownLatch.count == 0L) break

                    when (Random().nextInt().rem(6).absoluteValue) {
                        0 -> {
                            log.info("Forcefully killing node and proxy.")
                            (bankA as OutOfProcessImpl).onStopCallback()
                            (bankA as OutOfProcess).process.destroyForcibly()
                            tcpProxy.stop()
                            bankA = startBankA().get()
                            tcpProxy.start()
                        }
                        1 -> {
                            log.info("Forcefully killing node.")
                            (bankA as OutOfProcessImpl).onStopCallback()
                            (bankA as OutOfProcess).process.destroyForcibly()
                            bankA = startBankA().get()
                        }
                        2 -> {
                            log.info("Shutting down node.")
                            bankA.stop()
                            tcpProxy.stop()
                            bankA = startBankA().get()
                            tcpProxy.start()
                        }
                        3, 4 -> {
                            log.info("Killing proxy.")
                            tcpProxy.stop()
                            Thread.sleep(Random().nextInt(5000).toLong())
                            tcpProxy.start()
                        }
                        5 -> {
                            log.info("Dropping connection.")
                            tcpProxy.failConnection()
                        }
                    }
                    nrRestarts.incrementAndGet()
                }
            }

            // Start nrOfFlowsToRun and provide a logical retry function that checks the vault.
            val flowProgressEvents = mutableMapOf<StateMachineRunId, MutableList<String>>()
            for (amount in (1..nrOfFlowsToRun)) {
                // DOCSTART rpcReconnectingRPCFlowStarting
                bankAReconnectingRpc.runFlowWithLogicalRetry(
                        runFlow = { rpc ->
                            log.info("Starting CashIssueAndPaymentFlow for $amount")
                            val flowHandle = rpc.startTrackedFlowDynamic(
                                    CashIssueAndPaymentFlow::class.java,
                                    baseAmount.plus(Amount.parseCurrency("$amount USD")),
                                    issuerRef,
                                    bankB.nodeInfo.legalIdentities.first(),
                                    false,
                                    notary
                            )
                            val flowId = flowHandle.id
                            log.info("Started flow $amount with flowId: $flowId")
                            flowProgressEvents.addEvent(flowId, null)

                            // No reconnecting possible.
                            flowHandle.progress.subscribe(
                                    { prog ->
                                        flowProgressEvents.addEvent(flowId, prog)
                                        log.info("Progress $flowId : $prog")
                                    },
                                    { error ->
                                        log.error("Error thrown in the flow progress observer", error)
                                    })
                            flowHandle.id
                        },
                        hasFlowStarted = { rpc ->
                            // Query for a state that is the result of this flow.
                            val criteria = QueryCriteria.VaultCustomQueryCriteria(builder { CashSchemaV1.PersistentCashState::pennies.equal(amount.toLong() * 100) }, status = Vault.StateStatus.ALL)
                            val results = rpc.vaultQueryByCriteria(criteria, Cash.State::class.java)
                            log.info("$amount - Found states ${results.states}")
                            // The flow has completed if a state is found
                            results.states.isNotEmpty()
                        },
                        onFlowConfirmed = {
                            flowsCountdownLatch.countDown()
                            log.info("Flow started for $amount. Remaining flows: ${flowsCountdownLatch.count}")
                        }
                )
                // DOCEND rpcReconnectingRPCFlowStarting

                Thread.sleep(Random().nextInt(250).toLong())
            }

            log.info("Started all flows")

            // Wait until all flows have been started.
            flowsCountdownLatch.await()

            log.info("Confirmed all flows.")

            // Wait for all events to come in and flows to finish.
            Thread.sleep(4000)

            val nrFailures = nrRestarts.get()
            log.info("Checking results after $nrFailures restarts.")

            // Query the vault and check that states were created for all flows.
            fun readCashStates() = bankAReconnectingRpc
                    .vaultQueryByWithPagingSpec(Cash.State::class.java, QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.CONSUMED), PageSpecification(1, 10000))
                    .states

            var allCashStates = readCashStates()
            var nrRetries = 0

            // It might be necessary to wait more for all events to arrive when the node is slow.
            while (allCashStates.size < nrOfFlowsToRun && nrRetries++ < 50) {
                Thread.sleep(2000)
                allCashStates = readCashStates()
            }

            val allCash = allCashStates.map { it.state.data.amount.quantity }.toSet()
            val missingCash = (1..nrOfFlowsToRun).filterNot { allCash.contains(it.toLong() * 100) }
            log.info("MISSING: $missingCash")

            assertEquals(nrOfFlowsToRun, allCashStates.size, "Not all flows were executed successfully")

            // The progress status for each flow can only miss the last events, because the node might have been killed.
            val missingProgressEvents = flowProgressEvents.filterValues { expectedProgress.subList(0, it.size) != it }
            assertTrue(missingProgressEvents.isEmpty(), "The flow progress tracker is missing events: $missingProgressEvents")

            // DOCSTART missingVaultEvents
            // Check that enough vault events were received.
            // This check is fuzzy because events can go missing during node restarts.
            // Ideally there should be nrOfFlowsToRun events receive but some might get lost for each restart.
            assertTrue(vaultEvents!!.size + nrFailures * 3 >= nrOfFlowsToRun, "Not all vault events were received")
            // DOCEND missingVaultEvents

            // Check that no flow was triggered twice.
            val duplicates = allCashStates.groupBy { it.state.data.amount }.filterValues { it.size > 1 }
            assertTrue(duplicates.isEmpty(), "${duplicates.size} flows were retried illegally.")

            log.info("SM EVENTS: ${stateMachineEvents!!.size}")
            // State machine events are very likely to get lost more often because they seem to be sent with a delay.
            assertTrue(stateMachineEvents.count { it is StateMachineUpdate.Added } > nrOfFlowsToRun / 3, "Too many Added state machine events lost.")
            assertTrue(stateMachineEvents.count { it is StateMachineUpdate.Removed } > nrOfFlowsToRun / 3, "Too many Removed state machine events lost.")

            // Stop the observers.
            vaultObserverHandle.unsubscribe()
            stateMachineObserverHandle.unsubscribe()

            bankAReconnectingRpc.close()
        }

        tcpProxy.close()
    }

    @Synchronized
    fun MutableMap<StateMachineRunId, MutableList<String>>.addEvent(id: StateMachineRunId, progress: String?): Boolean {
        return getOrPut(id) { mutableListOf() }.let { if (progress != null) it.add(progress) else false }
    }
}
