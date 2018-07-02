package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Test

internal class RefState : Contract {
    companion object {
        val CONTRACT_ID = "net.corda.core.flows.RefState"
    }

    override fun verify(tx: LedgerTransaction) = Unit
    data class State(val owner: Party, val version: Int = 0, override val linearId: UniqueIdentifier = UniqueIdentifier()) : ReferenceState {
        override val participants: List<AbstractParty> get() = listOf(owner)
        fun update() = copy(version = version + 1)
    }

    class Create : CommandData
    class Update : CommandData
}

internal class CreateRefState : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        return subFlow(FinalityFlow(
                transaction = serviceHub.signInitialTransaction(TransactionBuilder(notary = notary).apply {
                    addOutputState(RefState.State(ourIdentity), RefState.CONTRACT_ID)
                    addCommand(RefState.Create(), listOf(ourIdentity.owningKey))
                })
        ))
    }
}

internal class UpdateRefState(val stateAndRef: StateAndRef<ReferenceState>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        return subFlow(FinalityFlow(
                transaction = serviceHub.signInitialTransaction(TransactionBuilder(notary = notary).apply {
                    addInputState(stateAndRef)
                    addOutputState((stateAndRef.state.data as RefState.State).update(), RefState.CONTRACT_ID)
                    addCommand(RefState.Update(), listOf(ourIdentity.owningKey))
                })
        ))
    }
}

internal object ShareRefState {
    @InitiatingFlow
    class Initiator(val stateAndRef: StateAndRef<ReferenceState>) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val sessions = serviceHub.networkMapCache.allNodes.flatMap { it.legalIdentities }.map { initiateFlow(it) }
            val transactionId = stateAndRef.ref.txhash
            val transaction = serviceHub.validatedTransactions.getTransaction(transactionId)
                    ?: throw FlowException("Cannot find $transactionId.")
            sessions.forEach { subFlow(SendTransactionFlow(it, transaction)) }
        }
    }

    @InitiatedBy(ShareRefState.Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("Receiving dependencies.")
            subFlow(ReceiveTransactionFlow(
                    otherSideSession = otherSession,
                    checkSufficientSignatures = true,
                    statesToRecord = StatesToRecord.ALL_VISIBLE
            ))
        }
    }
}

internal class UseRefState(val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val referenceState = serviceHub.vaultService.queryBy<ReferenceState>(query).states.single()
        return subFlow(FinalityFlow(
                transaction = serviceHub.signInitialTransaction(TransactionBuilder(notary = notary).apply {
                    addReferenceState(referenceState.referenced())
                    addOutputState(DummyState(), DummyContract.PROGRAM_ID)
                    addCommand(DummyContract.Commands.Create(), listOf(ourIdentity.owningKey))
                })
        ))
    }
}


class WithReferencedStatesFlowTests {
    private val mockNet = InternalMockNetwork(listOf("net.corda.core.flows", "net.corda.testing.contracts"), threadPerNode = true)
    private val nodes = (0..1).map { mockNet.createPartyNode() }

    @After
    fun stop() {
        mockNet.stopNodes()
    }

    @Test
    fun test() {
        // Create reference state.
        val newRefTx = nodes[0].services.startFlow(CreateRefState()).resultFuture.getOrThrow()
        println(newRefTx.tx)
        val newRefState = newRefTx.tx.outRefsOfType<RefState.State>().single()

        // Share it with others.
        nodes[0].services.startFlow(ShareRefState.Initiator(newRefState)).resultFuture.getOrThrow()

        // Update the reference state but don't share the update.
        val updatedRefTx = nodes[0].services.startFlow(UpdateRefState(newRefState)).resultFuture.getOrThrow()
        println(updatedRefTx.tx)
        val updatedRefState = updatedRefTx.tx.outRefsOfType<RefState.State>().single()

        // Try to use the old reference state. This will throw a NotaryException.
        val useRefTx = nodes[1].services.startFlow(WithReferencedStatesFlow(UseRefState(newRefState.state.data.linearId))).resultFuture

        // Give the UseRefState flow time to fail. We can't `GetOrThrow`
        // now because then we won't be able to get the result later on.
        Thread.sleep(500)

        // Share the update reference state.
        nodes[0].services.startFlow(ShareRefState.Initiator(updatedRefState)).resultFuture.getOrThrow()

        // Wait for the update to be stored by other nodes.
        println((useRefTx.getOrThrow() as SignedTransaction).tx)
    }

}