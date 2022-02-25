package net.corda.node.services.encryptedtx

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.toHexString
import net.corda.core.utilities.unwrap
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Before
import org.junit.Test
import java.lang.IllegalArgumentException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EncryptedBackchainTests {

    private lateinit var mockNet: MockNetwork
    private lateinit var bankOfCordaNode: StartedMockNode
    private lateinit var bankOfCorda: Party
    private lateinit var aliceNode: StartedMockNode
    private lateinit var alice: Party
    private lateinit var bobNode: StartedMockNode
    private lateinit var bob: Party

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin(), cordappsForAllNodes = listOf(enclosedCordapp())))
        bankOfCordaNode = mockNet.createPartyNode(BOC_NAME)
        bankOfCorda = bankOfCordaNode.info.identityFromX500Name(BOC_NAME)
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        alice = aliceNode.info.singleIdentity()
        bobNode = mockNet.createPartyNode(BOB_NAME)
        bob = bobNode.info.singleIdentity()
    }

    @Test
    fun `issue and move`() {

        val issuanceTx = bankOfCordaNode.execFlow(IssueFlow(300))

        val aliceTx = bankOfCordaNode.execFlow(MoveFlow(bankOfCordaNode.getAllTokens(), 200, alice))

        val bobTx = aliceNode.execFlow(MoveFlow(aliceNode.getAllTokens(), 100, bob))

        printTxs( listOf(
                "Bank issue to self: " to issuanceTx,
                "Bank pays Alice:    " to aliceTx,
                "Alice pays Bob:     " to bobTx)
        )
    }

    @Test
    fun `issue and move with remote signer`() {

        val issuanceTx = bankOfCordaNode.execFlow(IssueFlow(300))

        val aliceTx = bankOfCordaNode.execFlow(MoveFlow(bankOfCordaNode.getAllTokens(), 200, alice, true))

        val bobTx = aliceNode.execFlow(MoveFlow(aliceNode.getAllTokens(), 100, bob, true))

        printTxs( listOf(
                "Bank issue to self: " to issuanceTx,
                "Bank pays Alice:    " to aliceTx,
                "Alice pays Bob:     " to bobTx)
        )
    }

    @Test
    fun `bank of Corda cannot pay bob`() {

        bankOfCordaNode.execFlow(IssueFlow(300))

        val exception = assertFailsWith<FlowException>{
            bankOfCordaNode.execFlow(MoveFlow(bankOfCordaNode.getAllTokens(), 200, bob, true))
        }

        assertEquals("java.lang.IllegalArgumentException: Bank of Corda cannot move money to Bob", exception.message)
    }

    private fun printTxs(txHashes : List<Pair<String,SignedTransaction>>) {
        listOf(bankOfCordaNode, aliceNode, bobNode).forEach { node ->
            println("------------------------")
            println("${node.info.singleIdentity()}")
            println("------------------------")
            txHashes.forEach {  labelToStx ->
                val label = labelToStx.first
                val stx = labelToStx.second
                println("$label (${stx.id})")
                println("> FOUND UNENCRYPTED: ${node.services.validatedTransactions.getTransaction(stx.id)}")
                println("> FOUND   ENCRYPTED: ${node.services.validatedTransactions.getVerifiedEncryptedTransaction(stx.id)?.let { 
                    "${shortStringDesc(it.bytes.toHexString())} signature ${it.verifierSignature.toHexString()}"
                }}")

                println()
            }
            println()
        }
    }

    private fun <T> StartedMockNode.execFlow(flow : FlowLogic<T>) : T {
        val future = startFlow(flow)
        mockNet.runNetwork()
        return future.getOrThrow()
    }

    private fun StartedMockNode.getAllTokens() : List<StateAndRef<BasicToken>> {

        val allStates = services.vaultService.queryBy(BasicToken::class.java).states

        println(this.info.singleIdentity())
        allStates.forEach {
            println(it.state.data)
        }

        return allStates.filter {
            it.state.data.holder.owningKey == this.info.singleIdentity().owningKey
        }
    }

    private fun shortStringDesc(longString : String) : String {
        return "EncryptedTransaction(${longString.take(15)}...${longString.takeLast(15)})"
    }

    @CordaSerializable
    enum class SignaturesRequired {
        ALL,
        SENDER_ONLY
    }

    class BasicTokenContract: Contract {

        companion object {
            val contractId = this::class.java.enclosingClass.canonicalName
        }

        override fun verify(tx: LedgerTransaction) {
            val command = tx.commandsOfType(BasicTokenCommand::class.java).single()

            when (command.value) {
                is Issue -> {
                    val inputs = tx.inputsOfType<BasicToken>()
                    val outputs = tx.outputsOfType<BasicToken>()

                    require(inputs.isEmpty()) { "No input states allowed" }
                    require(outputs.isNotEmpty()) { "At least one BasicToken input state is required" }
                    require(outputs.all { it.amount > 0 }) { "Outputs must have amounts greater than zero" }

                }
                is Move -> {
                    val inputs = tx.inputsOfType<BasicToken>()
                    val outputs = tx.outputsOfType<BasicToken>()

                    require(inputs.isNotEmpty() && outputs.isNotEmpty()) { "Input and output states are required" }
                    require(inputs.sumBy { it.amount } == outputs.sumBy { it.amount }) { "Inputs and outputs must have the same value"}
                    require(command.signers.containsAll(inputs.map { it.holder.owningKey })) { "All holders must sign the tx" }

                    require(inputs.all { it.amount > 0 }) { "Inputs must have amounts greater than zero" }
                    require(outputs.all { it.amount > 0 }) { "Outputs must have amounts greater than zero" }
                    // no restriction on mixing issuers
                }
            }
        }
        open class BasicTokenCommand : CommandData
        class Issue : BasicTokenCommand()
        class Move : BasicTokenCommand()
    }

    @BelongsToContract(BasicTokenContract::class)
    class BasicToken(
            val amount: Int,
            val holder: AbstractParty,
            override val participants : List<AbstractParty> = listOf(holder)) : ContractState {

        override fun equals(other: Any?): Boolean {

            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BasicToken
            if (amount != other.amount) return false
            if (holder != other.holder) return false
            return true
        }
    }

    class IssueFlow(val amount: Int): FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call() : SignedTransaction {
            val ourKey = ourIdentity.owningKey

            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                    .addCommand(BasicTokenContract.Issue(), ourKey)
                    .addOutputState(BasicToken(amount, ourIdentity))
            val stx = serviceHub.signInitialTransaction(tx, ourKey)

            return subFlow(FinalityFlow(stx, emptyList()))
        }
    }

    @InitiatingFlow
    class MoveFlow(val inputs : List<StateAndRef<BasicToken>>,
                   val amount: Int,
                   val moveTo: AbstractParty,
                   val allMustSign : Boolean = false): FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call() : SignedTransaction{
            val ourKey = ourIdentity.owningKey

            val allMustSignStatus = if(allMustSign) SignaturesRequired.ALL else SignaturesRequired.SENDER_ONLY
            val signingKeys = if(allMustSign) listOf(ourKey, moveTo.owningKey) else listOf(ourKey)

            val changeAmount = inputs.sumBy { it.state.data.amount } - amount

            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                    .addCommand(BasicTokenContract.Move(), signingKeys)
                    .addOutputState(BasicToken(amount, moveTo))

            if (changeAmount > 0) {
                tx.addOutputState(BasicToken(changeAmount, ourIdentity))
            }

            inputs.forEach {
                tx.addInputState(it)
            }

            tx.verify(serviceHub)

            var stx = serviceHub.signInitialTransaction(tx, ourKey)

            val wtx = stx.tx

            val sessions = listOfNotNull(
                    serviceHub.identityService.wellKnownPartyFromAnonymous(moveTo)
            ).filter { it.owningKey != ourKey }.map { initiateFlow(it) }

            sessions.forEach {
                it.send(allMustSignStatus)

                if (allMustSign) {
                    stx = subFlow(CollectSignaturesFlow(stx , sessions))
                }
            }

            return subFlow(FinalityFlow(stx, sessions))
        }
    }

    @InitiatedBy(MoveFlow::class)
    class MoveHandler(val otherSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {

                val requiresSignature = otherSession.receive(SignaturesRequired::class.java).unwrap { it }

                if (requiresSignature == SignaturesRequired.ALL) {

                    subFlow(object : SignTransactionFlow(otherSession, encrypted = true) {
                                override fun checkTransaction(stx: SignedTransaction) {
                                    val inputs = stx.tx.inputsStates.filterIsInstance<StateAndRef<BasicToken>>()
                                    val outputs = stx.tx.outputsOfType(BasicToken::class.java)

                                    // a test condition we can use to trigger a signature failure
                                    require(!(inputs.any { serviceHub.identityService.wellKnownPartyFromAnonymous(it.state.data.holder)?.name == BOC_NAME } &&
                                            outputs.any { serviceHub.identityService.wellKnownPartyFromAnonymous(it.holder)?.name == BOB_NAME })){
                                        "Bank of Corda cannot move money to Bob"
                                    }
                                }
                            }
                    )
                }

                subFlow(ReceiveFinalityFlow(otherSideSession = otherSession))
            }
        }
    }
}