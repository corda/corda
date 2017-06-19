package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.contracts.TransactionType.General
import net.corda.core.contracts.TransactionType.NotaryChange
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_PUBKEY_1
import net.corda.core.utilities.DUMMY_REGULATOR
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import org.bouncycastle.asn1.x500.X500Name
import java.time.Instant

val DUMMY_STATE = DummyState()
val DUMMY_STATE_REF = StateRef(SecureHash.sha256("DummyTransactionHash"), 0)
val DUMMY_STATE_AND_REF = StateAndRef(TransactionState(DummyState(), DUMMY_NOTARY), DUMMY_STATE_REF)
val DUMMY_COMMAND = Command(DummyContract.Commands.Create(), listOf(DUMMY_PUBKEY_1))
val DUMMY_TIME_WINDOW = TimeWindow.between(Instant.MIN, Instant.MAX)

object MyFlowPair {
    @InitiatingFlow
    @StartableByRPC
    class InitiatorFlow(val arg1: Boolean, val arg2: Int, val counterparty: Party) : FlowLogic<Unit>() {

        companion object {
            object TX_BUILDING : Step("Building a transaction.")
            object TX_SIGNING : Step("Signing a transaction.")
            object TX_VERIFICATION : Step("Verifying a transaction.")
            object SIGS_GATHERING : Step("Gathering a transaction's signatures.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISATION : Step("Finalising a transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    TX_BUILDING,
                    TX_SIGNING,
                    TX_VERIFICATION,
                    SIGS_GATHERING,
                    FINALISATION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call() {
            /**--------------------
             * TRANSACTION BUILDING
            ---------------------*/
            progressTracker.currentStep = TX_BUILDING
            // A transaction generally needs a notary:
            //   - To prevent double-spends if the transaction has inputs
            //   - To serve as a timestamping authority if the transaction has a time-window
            val notary1 = serviceHub.networkMapCache.getAnyNotary()
            val notary2 = serviceHub.networkMapCache.notaryNodes[0].notaryIdentity
            val notary3 = serviceHub.networkMapCache.getNotary(X500Name("CN=Notary Service,O=R3,OU=corda,L=London,C=UK"))

            // There are two types of transaction,
            // and therefore two types of transaction builder:
            val notaryChangeTxBuilder = TransactionBuilder(NotaryChange, DUMMY_NOTARY)
            val regTxBuilder = TransactionBuilder(General, DUMMY_NOTARY)

            // We add items to the transaction builder using ``TransactionBuilder.withItems``:
            regTxBuilder.withItems(
                    // Inputs, as ``StateRef``s that reference to the outputs of previous transactions
                    DUMMY_STATE_REF,
                    // Outputs, as ``ContractState``s
                    DUMMY_STATE,
                    // Commands, as ``Command``s
                    DUMMY_COMMAND
            )

            // We can also add items using methods for the individual components:
            regTxBuilder.addInputState(DUMMY_STATE_AND_REF)
            regTxBuilder.addOutputState(DUMMY_STATE)
            regTxBuilder.addCommand(DUMMY_COMMAND)
            regTxBuilder.addAttachment(SecureHash.sha256("DummyAttachment"))
            regTxBuilder.addTimeWindow(DUMMY_TIME_WINDOW)

            /**-------------------
             * TRANSACTION SIGNING
            --------------------*/
            progressTracker.currentStep = TX_SIGNING
            // We finalise the transaction by signing it,
            // converting it into a ``SignedTransaction``.
            val partSignedTx = serviceHub.signInitialTransaction(regTxBuilder)

            /**------------------------
             * TRANSACTION VERIFICATION
            -------------------------*/
            progressTracker.currentStep = TX_VERIFICATION
            // A ``SignedTransaction`` is a pairing of a ``WireTransaction``
            // with signatures over this ``WireTransaction``.
            // We don't verify a signed transaction per se, but rather the ``WireTransaction`` it contains.
            val wireTx = partSignedTx.tx
            // Before we can verify the transaction, we have to resolve its inputs and attachments
            // into actual objects, rather than just references.
            // We achieve this by converting the ``WireTransaction`` into a ``LedgerTransaction``.
            val ledgerTx = wireTx.toLedgerTransaction(serviceHub)
            // We can now verify the transaction.
            ledgerTx.verify()

            /**--------------------
             * GATHERING SIGNATURES
            ---------------------*/
            progressTracker.currentStep = SIGS_GATHERING
            // Given a signed transaction, we can automatically gather the signatures of all
            // the participants of all the transaction's states who haven't yet signed.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, SIGS_GATHERING.childProgressTracker()))

            /**--------------------------
             * FINALISING THE TRANSACTION
            ---------------------------*/
            progressTracker.currentStep = FINALISATION
            // We notarise the transaction and get it recorded in the vault of
            // all the participants of all the transaction's states.
            val notarisedTx1 = subFlow(FinalityFlow(fullySignedTx, FINALISATION.childProgressTracker()))
            // We can also choose to send it to additional parties who aren't one
            // of the state's participants.
            val notarisedTx2 = subFlow(FinalityFlow(listOf(fullySignedTx), setOf(DUMMY_REGULATOR), FINALISATION.childProgressTracker()))
        }
    }

    @InitiatedBy(InitiatorFlow::class)
    class ResponderFlow(val otherParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {

        }
    }
}