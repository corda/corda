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
import net.corda.core.utilities.*
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import org.bouncycastle.asn1.x500.X500Name
import java.time.Instant

// We group our two flows inside a singleton object to indicate that they work
// together.
object MyFlowPair {
    // ``InitiatorFlow`` is our first flow.
    // We mark ``InitiatorFlow`` as an ``InitiatingFlow``, allowing it to be
    // started directly by the node.
    @InitiatingFlow
    // We also mark ``InitiatorFlow`` as ``StartableByRPC``, allowing the
    // node's owner to start the flow via RPC.
    @StartableByRPC
    // Every flow must subclass ``FlowLogic``. The generic indicates the
    // flow's return type.
    class InitiatorFlow(val arg1: Boolean, val arg2: Int, val counterparty: Party) : FlowLogic<Unit>() {

        /**---------------------------------
         * WIRING UP THE PROGRESS TRACKER *
        ---------------------------------**/
        companion object {
            object ID_OTHER_NODES : Step("Identifying other nodes on the network.")
            object TX_COMPONENTS : Step("Gathering a transaction's components.")
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
                    ID_OTHER_NODES,
                    TX_COMPONENTS,
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
            /**--------------------------
             * IDENTIFYING OTHER NODES *
            --------------------------**/
            progressTracker.currentStep = ID_OTHER_NODES
            // A transaction generally needs a notary:
            //   - To prevent double-spends if the transaction has inputs
            //   - To serve as a timestamping authority if the transaction has a time-window
            // We retrieve the notary from the network map.
            val specificNotary = serviceHub.networkMapCache.getNotary(X500Name("CN=Notary Service,O=R3,OU=corda,L=London,C=UK"))
            val anyNotary = serviceHub.networkMapCache.getAnyNotary()
            val firstNotary = serviceHub.networkMapCache.notaryNodes[0].notaryIdentity

            // We may also need to identify a specific counterparty.
            // Again, we do so using the network map.
            val namedCounterparty = serviceHub.networkMapCache.getNodeByLegalName(X500Name("CN=NodeA,O=NodeA,L=London,C=UK"))
            val keyedCounterparty = serviceHub.networkMapCache.getNodeByLegalIdentityKey(DUMMY_PUBKEY_1)
            val firstCounterparty = serviceHub.networkMapCache.partyNodes[0]

            /**-----------------------------------
             * GATHERING TRANSACTION COMPONENTS *
            -----------------------------------**/
            progressTracker.currentStep = TX_COMPONENTS
            // Output states are constructed from scratch.
            val DUMMY_OUTPUT = DummyState()

            // Commands are the pairing of a ``CommandData`` type, and the list of signers.
            val DUMMY_COMMAND = Command(DummyContract.Commands.Create(), listOf(DUMMY_PUBKEY_1))

            // Input states are identified using ``StateRef`` instances,
            // which pair the hash of the transaction that generated the state
            // with the state's index in the outputs of that transaction.
            val DUMMY_STATE_REF = StateRef(SecureHash.sha256("DummyTransactionHash"), 0)
            // A ``StateAndRef`` pairs a ``StateRef`` with the state it points to.
            val DUMMY_STATE_AND_REF = StateAndRef(TransactionState(DummyState(), DUMMY_NOTARY), DUMMY_STATE_REF)

            // TODO: Getting stuff from the vault

            // Attachments are identified by their hash.
            val DUMMY_ATTACHMENT = SecureHash.sha256("DummyAttachment")

            // TODO: Uploading attachments

            // Timewindows can have a start and end time, or be open at either end.
            val DUMMY_TIME_WINDOW = TimeWindow.between(Instant.MIN, Instant.MAX)
            val DUMMY_AFTER = TimeWindow.fromOnly(Instant.MIN)
            val DUMMY_BEFORE = TimeWindow.untilOnly(Instant.MAX)

            /**-----------------------
             * TRANSACTION BUILDING *
            -----------------------**/
            progressTracker.currentStep = TX_BUILDING
            // There are two types of transaction (notary-change and general),
            // and therefore two types of transaction builder:
            val notaryChangeTxBuilder = TransactionBuilder(NotaryChange, DUMMY_NOTARY)
            val regTxBuilder = TransactionBuilder(General, DUMMY_NOTARY)

            // We add items to the transaction builder using ``TransactionBuilder.withItems``:
            regTxBuilder.withItems(
                    // Inputs, as ``StateRef``s that reference to the outputs of previous transactions
                    DUMMY_STATE_REF,
                    // Outputs, as ``ContractState``s
                    DUMMY_OUTPUT,
                    // Commands, as ``Command``s
                    DUMMY_COMMAND
            )

            // We can also add items using methods for the individual components:
            regTxBuilder.addInputState(DUMMY_STATE_AND_REF)
            regTxBuilder.addOutputState(DUMMY_OUTPUT)
            regTxBuilder.addCommand(DUMMY_COMMAND)
            regTxBuilder.addAttachment(DUMMY_ATTACHMENT)
            regTxBuilder.addTimeWindow(DUMMY_TIME_WINDOW)

            /**----------------------
             * TRANSACTION SIGNING *
            ----------------------**/
            progressTracker.currentStep = TX_SIGNING
            // We finalise the transaction by signing it,
            // converting it into a ``SignedTransaction``.
            val partSignedTx = serviceHub.signInitialTransaction(regTxBuilder)

            /**---------------------------
             * TRANSACTION VERIFICATION *
            ---------------------------**/
            progressTracker.currentStep = TX_VERIFICATION
            // We can verify a transaction using the following one-liner:
            partSignedTx.tx.toLedgerTransaction(serviceHub).verify()

            // Let's break that down...

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

            /**-----------------------
             * GATHERING SIGNATURES *
            -----------------------**/
            progressTracker.currentStep = SIGS_GATHERING
            // Given a signed transaction, we can automatically gather the signatures of all
            // the participants of all the transaction's states who haven't yet signed.
            // The responder flow will need to call ``SignTransactionFlow``.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, SIGS_GATHERING.childProgressTracker()))

            // If ``CollectSignaturesFlow`` does not meet our requirements,
            // we can also send and receive arbitrary data.

            // We can send arbitrary data to a counterparty.
            send(DUMMY_BANK_A, Any())
            // We can wait to receive arbitrary data of a specific type from a
            // counterparty.
            val data1: UntrustworthyData<Any> = receive<Any>(DUMMY_BANK_B)
            // We receive the data wrapped in an ``UntrustworthyData``
            // instance, which we must unwrap using a lambda.
            val any1: Any = data1.unwrap { data ->
                // Perform checking on the object received.
                // T O D O: Check the received object.
                // Return the object.
                data
            }
            // We can also send data to a counterparty and wait to receive
            // data of a specific type back.
            val data2: UntrustworthyData<Any> = sendAndReceive<Any>(DUMMY_BANK_C, Any())
            val any2: Any = data2.unwrap { data ->
                // Perform checking on the object received.
                // T O D O: Check the received object.
                // Return the object.
                data
            }

            /**-----------------------------
             * FINALISING THE TRANSACTION *
            -----------------------------**/
            progressTracker.currentStep = FINALISATION
            // We notarise the transaction and get it recorded in the vault of
            // all the participants of all the transaction's states.
            val notarisedTx1 = subFlow(FinalityFlow(fullySignedTx, FINALISATION.childProgressTracker()))
            // We can also choose to send it to additional parties who aren't one
            // of the state's participants.
            val additionalParties = setOf(DUMMY_REGULATOR, DUMMY_BANK_A)
            val notarisedTx2 = subFlow(FinalityFlow(listOf(fullySignedTx), additionalParties, FINALISATION.childProgressTracker()))
        }
    }

    // ``ResponderFlow`` is our first flow.
    // We mark ``ResponderFlow`` as an ``InitiatedByFlow``, meaning that it
    // can only be started in response to a message from its initiating flow.
    // That's ``InitiatorFlow`` in this case.
    @InitiatedBy(InitiatorFlow::class)
    class ResponderFlow(val otherParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // TODO: Fill this out
        }
    }
}
