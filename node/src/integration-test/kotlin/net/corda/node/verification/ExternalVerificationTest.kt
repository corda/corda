package net.corda.node.verification

import co.paralleluniverse.fibers.Suspendable
import com.typesafe.config.ConfigFactory
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionVerificationException.ContractRejection
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryChangeFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.node.NodeInfo
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.io.File
import java.net.InetAddress
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class ExternalVerificationTest {
    @Test(timeout=300_000)
    fun `regular transactions are verified in external verifier`() {
        internalDriver(
                systemProperties = mapOf("net.corda.node.verification.external" to "true"),
                cordappsForAllNodes = FINANCE_CORDAPPS,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true, startInProcess = false))
        ) {
            val (notary, alice, bob) = listOf(
                    defaultNotaryNode,
                    startNode(NodeParameters(providedName = ALICE_NAME)),
                    startNode(NodeParameters(providedName = BOB_NAME))
            ).transpose().getOrThrow()

            val (issuanceTx) = alice.rpc.startFlow(
                    ::CashIssueFlow,
                    10.DOLLARS,
                    OpaqueBytes.of(0x01),
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()

            val (paymentTx) = alice.rpc.startFlow(
                    ::CashPaymentFlow,
                    10.DOLLARS,
                    bob.nodeInfo.singleIdentity(),
                    false,
            ).returnValue.getOrThrow()

            notary.assertTransactionsWereVerifiedExternally(issuanceTx.id, paymentTx.id)
            bob.assertTransactionsWereVerifiedExternally(issuanceTx.id, paymentTx.id)
        }
    }

    @Test(timeout=300_000)
    fun `external verifier is unable to verify contracts which use new Kotlin APIs`() {
        check(!IntArray::maxOrNull.isInline)

        internalDriver(
                systemProperties = mapOf("net.corda.node.verification.external" to "true"),
                cordappsForAllNodes = listOf(cordappWithPackages("net.corda.node.verification"))
        ) {
            val (alice, bob) = listOf(
                    startNode(NodeParameters(providedName = ALICE_NAME)),
                    startNode(NodeParameters(providedName = BOB_NAME)),
            ).transpose().getOrThrow()

            assertThatExceptionOfType(UnexpectedFlowEndException::class.java).isThrownBy {
                alice.rpc.startFlow(::NewKotlinApiFlow, bob.nodeInfo).returnValue.getOrThrow()
            }

            assertThat(bob.externalVerifierLogs()).contains("""
                java.lang.NoSuchMethodError: 'java.lang.Integer kotlin.collections.ArraysKt.maxOrNull(int[])'
                	at net.corda.node.verification.ExternalVerificationTest${'$'}NewKotlinApiContract.verify(ExternalVerificationTest.kt:
            """.trimIndent())
        }
    }

    @Test(timeout=300_000)
    fun `regular transactions can fail verification in external verifier`() {
        internalDriver(
                systemProperties = mapOf("net.corda.node.verification.external" to "true"),
                cordappsForAllNodes = listOf(cordappWithPackages("net.corda.node.verification", "com.typesafe.config"))
        ) {
            val (alice, bob, charlie) = listOf(
                    startNode(NodeParameters(providedName = ALICE_NAME)),
                    startNode(NodeParameters(providedName = BOB_NAME)),
                    startNode(NodeParameters(providedName = CHARLIE_NAME))
            ).transpose().getOrThrow()

            // Create a transaction from Alice to Bob, where Charlie is specified as the contract verification trigger
            val firstState = alice.rpc.startFlow(::FailExternallyFlow, null, charlie.nodeInfo, bob.nodeInfo).returnValue.getOrThrow()
            // When the transaction chain tries to moves onto Charlie, it will trigger the failure
            assertThatExceptionOfType(ContractRejection::class.java)
                    .isThrownBy { bob.rpc.startFlow(::FailExternallyFlow, firstState, charlie.nodeInfo, charlie.nodeInfo).returnValue.getOrThrow() }
                    .withMessageContaining("Fail in external verifier: ${firstState.ref.txhash}")

            // Make sure Charlie tried to verify the first transaction externally
            assertThat(charlie.externalVerifierLogs()).contains("Fail in external verifier: ${firstState.ref.txhash}")
        }
    }

    @Test(timeout=300_000)
    fun `notary change transactions are verified in external verifier`() {
        internalDriver(
                systemProperties = mapOf("net.corda.node.verification.external" to "true"),
                cordappsForAllNodes = FINANCE_CORDAPPS + enclosedCordapp(),
                notarySpecs = listOf(DUMMY_NOTARY_NAME, BOC_NAME).map { NotarySpec(it, validating = true, startInProcess = false) }
        ) {
            val (notary1, notary2) = notaryHandles.map { handle -> handle.nodeHandles.map { it[0] } }.transpose().getOrThrow()
            val alice = startNode(NodeParameters(providedName = ALICE_NAME)).getOrThrow()

            val txId = alice.rpc.startFlow(
                    ::IssueAndChangeNotaryFlow,
                    notary1.nodeInfo.singleIdentity(),
                    notary2.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow()

            notary1.assertTransactionsWereVerifiedExternally(txId)
            alice.assertTransactionsWereVerifiedExternally(txId)
        }
    }

    private fun NodeHandle.assertTransactionsWereVerifiedExternally(vararg txIds: SecureHash) {
        val verifierLogContent = externalVerifierLogs()
        for (txId in txIds) {
            assertThat(verifierLogContent).contains("SignedTransaction(id=$txId) verified")
        }
    }

    private fun NodeHandle.externalVerifierLogs(): String {
        val verifierLogs = (baseDirectory / "logs")
                .listDirectoryEntries()
                .filter { it.name == "verifier-${InetAddress.getLocalHost().hostName}.log" }
        assertThat(verifierLogs).describedAs("External verifier was not started").hasSize(1)
        return verifierLogs[0].readText()
    }


    class FailExternallyContract : Contract {
        override fun verify(tx: LedgerTransaction) {
            val command = tx.commandsOfType<Command>().single()
            if (insideExternalVerifier()) {
                // The current directory for the external verifier is the node's base directory
                val localName = CordaX500Name.parse(ConfigFactory.parseFile(File("node.conf")).getString("myLegalName"))
                check(localName != command.value.failForParty.name) { "Fail in external verifier: ${tx.id}" }
            }
        }

        private fun insideExternalVerifier(): Boolean {
            return StackWalker.getInstance().walk { frames ->
                frames.anyMatch { it.className.startsWith("net.corda.verifier.") }
            }
        }

        data class State(override val party: Party) : TestState
        data class Command(val failForParty: Party) : CommandData
    }


    @StartableByRPC
    @InitiatingFlow
    class FailExternallyFlow(inputState: StateAndRef<FailExternallyContract.State>?,
                             private val failForParty: NodeInfo,
                             recipient: NodeInfo) : TestFlow<FailExternallyContract.State>(inputState, recipient) {
        override fun newOutput() = FailExternallyContract.State(serviceHub.myInfo.legalIdentities[0])
        override fun newCommand() = FailExternallyContract.Command(failForParty.legalIdentities[0])

        @Suppress("unused")
        @InitiatedBy(FailExternallyFlow::class)
        class ReceiverFlow(otherSide: FlowSession) : TestReceiverFlow(otherSide)
    }


    class NewKotlinApiContract : Contract {
        override fun verify(tx: LedgerTransaction) {
            check(tx.commandsOfType<Command>().isNotEmpty())
            // New post-1.2 API which is non-inlined
            intArrayOf().maxOrNull()
        }

        data class State(override val party: Party) : TestState
        object Command : TypeOnlyCommandData()
    }


    @StartableByRPC
    @InitiatingFlow
    class NewKotlinApiFlow(recipient: NodeInfo) : TestFlow<NewKotlinApiContract.State>(null, recipient) {
        override fun newOutput() = NewKotlinApiContract.State(serviceHub.myInfo.legalIdentities[0])
        override fun newCommand() = NewKotlinApiContract.Command

        @Suppress("unused")
        @InitiatedBy(NewKotlinApiFlow::class)
        class ReceiverFlow(otherSide: FlowSession) : TestReceiverFlow(otherSide)
    }


    @StartableByRPC
    class IssueAndChangeNotaryFlow(private val oldNotary: Party, private val newNotary: Party) : FlowLogic<SecureHash>() {
        @Suspendable
        override fun call(): SecureHash {
            subFlow(CashIssueFlow(10.DOLLARS, OpaqueBytes.of(0x01), oldNotary))
            val oldState = serviceHub.vaultService.queryBy(Cash.State::class.java).states.single()
            assertThat(oldState.state.notary).isEqualTo(oldNotary)
            val newState = subFlow(NotaryChangeFlow(oldState, newNotary))
            assertThat(newState.state.notary).isEqualTo(newNotary)
            val notaryChangeTx = serviceHub.validatedTransactions.getTransaction(newState.ref.txhash)
            assertThat(notaryChangeTx?.coreTransaction).isInstanceOf(NotaryChangeWireTransaction::class.java)
            return notaryChangeTx!!.id
        }
    }


    abstract class TestFlow<T : TestState>(
            private val inputState: StateAndRef<T>?,
            private val recipient: NodeInfo
    ) : FlowLogic<StateAndRef<T>>() {
        @Suspendable
        override fun call(): StateAndRef<T> {
            val myParty = serviceHub.myInfo.legalIdentities[0]
            val txBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities[0])
            inputState?.let(txBuilder::addInputState)
            txBuilder.addOutputState(newOutput())
            txBuilder.addCommand(newCommand(), myParty.owningKey)
            val initialTx = serviceHub.signInitialTransaction(txBuilder)
            val sessions = arrayListOf(initiateFlow(recipient.legalIdentities[0]))
            inputState?.let { sessions += initiateFlow(it.state.data.party) }
            val notarisedTx = subFlow(FinalityFlow(initialTx, sessions))
            return notarisedTx.toLedgerTransaction(serviceHub).outRef(0)
        }

        protected abstract fun newOutput(): T
        protected abstract fun newCommand(): CommandData
    }

    abstract class TestReceiverFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }

    interface TestState : ContractState {
        val party: Party
        override val participants: List<AbstractParty> get() = listOf(party)
    }
}
