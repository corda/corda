package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CancelPeerReceiveTransactionFlowException
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryException
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.cancelPeerReceiveTransactionFlows
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CancelReceiveFinalityFlowTest {

    @Suppress("DEPRECATION")
    @Test(timeout = 300_000)
    fun `cancelling peers in receive finality flow`() {
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(
            DriverParameters(
                isDebug = false,
                startNodesInProcess = true,
                cordappsForAllNodes = listOf(enclosedCordapp(), cordappWithPackages(DummyContract::class.packageName))
            )
        ) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeCHandle = startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val peers = listOf(nodeBHandle.nodeInfo.singleIdentity(), nodeCHandle.nodeInfo.singleIdentity())
            nodeAHandle.rpc.let {
                val ref = it.startFlow(::CreateTransactionFlow, peers).returnValue.getOrThrow(Duration.of(20, ChronoUnit.SECONDS))
                it.startFlow(::DoubleSpendAndCatchFlow, peers, ref).returnValue.getOrThrow(Duration.of(20, ChronoUnit.SECONDS))
                it.startFlow(::DoubleSpendAndCatchFlow, peers, ref).returnValue.getOrThrow(Duration.of(20, ChronoUnit.SECONDS))

                DoubleSpendAndCatchResponderFlow.locks.forEach { (_, lock) ->
                    lock.await(30, TimeUnit.SECONDS)
                }

                assertTrue(DoubleSpendAndCatchResponderFlow.receivedCancelException[BOB_NAME]!!)
                assertTrue(DoubleSpendAndCatchResponderFlow.receivedCancelException[CHARLIE_NAME]!!)

                assertEquals(2, it.internalVerifiedTransactionsSnapshot().size)
            }

            assertEquals(2, nodeBHandle.rpc.internalVerifiedTransactionsSnapshot().size)
            assertEquals(2, nodeCHandle.rpc.internalVerifiedTransactionsSnapshot().size)
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class CreateTransactionFlow(private val peers: List<Party>) : FlowLogic<StateAndRef<DummyState>>() {
        @Suspendable
        override fun call(): StateAndRef<DummyState> {
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addOutputState(DummyState(participants = listOf(ourIdentity)))
                addCommand(DummyContract.Commands.Create(), peers.map { it.owningKey } + ourIdentity.owningKey)
            }
            val stx = serviceHub.signInitialTransaction(tx)
            val sessions = peers.map { initiateFlow(it) }
            val ftx = subFlow(CollectSignaturesFlow(stx, sessions))
            subFlow(FinalityFlow(ftx, sessions))

            return ftx.coreTransaction.outRef(0)
        }
    }

    @InitiatedBy(CreateTransactionFlow::class)
    class CreateTransactionResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val stx = subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                }
            })
            subFlow(ReceiveFinalityFlow(session, stx.id))
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class DoubleSpendAndCatchFlow(private val peers: List<Party>, private val ref: StateAndRef<DummyState>) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addInputState(ref)
                addCommand(DummyContract.Commands.Move(), peers.map { it.owningKey } + ourIdentity.owningKey)
            }
            val stx = serviceHub.signInitialTransaction(tx)
            val sessions = peers.map { initiateFlow(it) }
            val ftx = subFlow(CollectSignaturesFlow(stx, sessions))
            try {
                subFlow(FinalityFlow(ftx, sessions))
            } catch (e: NotaryException) {
                cancelPeerReceiveTransactionFlows(sessions, ourIdentity.owningKey)
            }
        }
    }

    @InitiatedBy(DoubleSpendAndCatchFlow::class)
    class DoubleSpendAndCatchResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {

        companion object {
            val locks = ConcurrentHashMap<CordaX500Name, CountDownLatch>()
            var receivedCancelException = ConcurrentHashMap<CordaX500Name, Boolean>().apply {
                this[BOB_NAME] = false
                this[CHARLIE_NAME] = false
            }
        }

        @Suspendable
        override fun call() {
            val stx = subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                }
            })
            try {
                subFlow(ReceiveFinalityFlow(session, stx.id))
            } catch (e: CancelPeerReceiveTransactionFlowException) {
                receivedCancelException[ourIdentity.name] = true
                locks[ourIdentity.name]!!.countDown()
            }
        }
    }
}