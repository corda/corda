package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.contracts.TransactionType.General
import net.corda.core.contracts.TransactionType.NotaryChange
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.*
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.ResolveTransactionsFlow
import net.corda.flows.SignTransactionFlow
import org.bouncycastle.asn1.x500.X500Name
import java.security.PublicKey
import java.time.Instant

// We group our two flows inside a singleton object to indicate that they work
// together.
object FlowCookbook {
    // ``InitiatorFlow`` is our first flow, and will communicate with
    // ``ResponderFlow``, below.
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
            object SENDING_AND_RECEIVING_DATA : Step("Sending data between parties.")
            object EXTRACTING_VAULT_STATES : Step("Extracting states from the vault.")
            object OTHER_TX_COMPONENTS : Step("Gathering a transaction's other components.")
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
                    SENDING_AND_RECEIVING_DATA,
                    EXTRACTING_VAULT_STATES,
                    OTHER_TX_COMPONENTS,
                    TX_BUILDING,
                    TX_SIGNING,
                    TX_VERIFICATION,
                    SIGS_GATHERING,
                    FINALISATION
            )
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call() {
            // We'll be using some dummy party and public key objects for
            // demonstration purposes. These are built in to Corda, and are
            // generally used for writing tests.
            val dummyBank: Party = DUMMY_BANK_A
            val dummyRegulator: Party = DUMMY_REGULATOR
            val dummyPubKey: PublicKey = DUMMY_PUBKEY_1

            /**--------------------------
             * IDENTIFYING OTHER NODES *
            --------------------------**/
            progressTracker.currentStep = ID_OTHER_NODES

            // A transaction generally needs a notary:
            //   - To prevent double-spends if the transaction has inputs
            //   - To serve as a timestamping authority if the transaction has a time-window
            // We retrieve the notary from the network map.
            val specificNotary: Party? = serviceHub.networkMapCache.getNotary(X500Name("CN=Notary Service,O=R3,OU=corda,L=London,C=UK"))
            val anyNotary: Party? = serviceHub.networkMapCache.getAnyNotary()
            // Unlike the first two methods, ``getNotaryNodes`` returns a
            // ``List<NodeInfo>``. We have to extract the notary identity of
            // the node we want.
            val firstNotary: Party = serviceHub.networkMapCache.notaryNodes[0].notaryIdentity

            // We may also need to identify a specific counterparty.
            // Again, we do so using the network map.
            val namedCounterparty: Party? = serviceHub.networkMapCache.getNodeByLegalName(X500Name("CN=NodeA,O=NodeA,L=London,C=UK"))?.legalIdentity
            val keyedCounterparty: Party? = serviceHub.networkMapCache.getNodeByLegalIdentityKey(dummyPubKey)?.legalIdentity
            val firstCounterparty: Party = serviceHub.networkMapCache.partyNodes[0].legalIdentity

            /**-----------------------------
             * SENDING AND RECEIVING DATA *
            -----------------------------**/
            progressTracker.currentStep = SENDING_AND_RECEIVING_DATA

            // We can send arbitrary data to a counterparty.
            // If this is the first ``send``, the counterparty will either:
            // 1. Ignore the message if they are not registered to respond
            //    to messages from this flow.
            // 2. Start the flow they have registered to respond to this flow,
            //    and run the flow until the first call to ``receive``, at
            //    which point they process the message.
            // In other words, we are assuming that the counterparty is
            // registered to respond to this flow, and has a corresponding
            // ``receive`` call.
            send(counterparty, Any())

            // We can wait to receive arbitrary data of a specific type from a
            // counterparty. Again, this implies a corresponding ``send`` call
            // in the counterparty's flow. If the payload we receive is not of
            // the specified type, a ``FlowException`` is raised.
            val packet1: UntrustworthyData<Int> = receive<Int>(counterparty)
            // We receive the data wrapped in an ``UntrustworthyData``
            // instance, which we must unwrap using a lambda.
            val int: Int = packet1.unwrap { data ->
                // Perform checking on the object received.
                // T O D O: Check the received object.
                // Return the object.
                data
            }

            // We can also send data to a counterparty and wait to receive
            // data of a specific type back. The type of data sent doesn't
            // need to match the type of the data received back.
            val packet2: UntrustworthyData<Boolean> = sendAndReceive<Boolean>(counterparty, "You can send and receive any class!")
            val boolean: Boolean = packet2.unwrap { data ->
                // Perform checking on the object received.
                // T O D O: Check the received object.
                // Return the object.
                data
            }

            // We're not limited to sending to and receiving from a single
            // counterparty. A flow can send messages to as many parties as it
            // likes, and they can each invoke a different response flow.
            send(dummyBank, Any())
            val packet3: UntrustworthyData<Any> = receive<Any>(dummyRegulator)

            /**-----------------------------------
             * EXTRACTING STATES FROM THE VAULT *
            -----------------------------------**/
            progressTracker.currentStep = EXTRACTING_VAULT_STATES

            // TODO: Retrieving from the vault

            // Input states are identified using ``StateRef`` instances,
            // which pair the hash of the transaction that generated the state
            // with the state's index in the outputs of that transaction.
            val ourStateRef: StateRef = StateRef(SecureHash.sha256("DummyTransactionHash"), 0)
            // A ``StateAndRef`` pairs a ``StateRef`` with the state it points to.
            val ourStateAndRef: StateAndRef<DummyState> = serviceHub.toStateAndRef<DummyState>(ourStateRef)

            /**-----------------------------------------
             * GATHERING OTHER TRANSACTION COMPONENTS *
            -----------------------------------------**/
            progressTracker.currentStep = OTHER_TX_COMPONENTS

            // Output states are constructed from scratch.
            val ourOutput: ContractState = DummyState()
            // Or copied from input states with some properties changed.
            // TODO: Wait until vault stuff is ready.

            // Commands pair a ``CommandData`` instance with a list of
            // public keys. To be valid, the transaction requires a signature
            // matching every public key in all of the transaction's commands.
            val commandData: CommandData = DummyContract.Commands.Create()
            val ourPubKey: PublicKey = serviceHub.legalIdentityKey
            val counterpartyPubKey: PublicKey = counterparty.owningKey
            val requiredSigners: List<PublicKey> = listOf(ourPubKey, counterpartyPubKey)
            val ourCommand: Command = Command(commandData, requiredSigners)

            // ``CommandData`` can either be:
            // 1. Of type ``TypeOnlyCommandData``, in which case it only
            //    serves to attach signers to the transaction and possibly
            //    fork the contract's verification logic.
            val typeOnlyCommandData: TypeOnlyCommandData = DummyContract.Commands.Create()
            // 2. Include additional data which can be used by the contract
            //    during verification, alongside fulfilling the roles above
            val commandDataWithData: CommandData = Cash.Commands.Issue(nonce = 12345678)

            // Attachments are identified by their hash.
            // The attachment with the corresponding hash must have been
            // uploaded ahead of time via the node's RPC interface.
            val ourAttachment: SecureHash = SecureHash.sha256("DummyAttachment")

            // Time windows can have a start and end time, or be open at either end.
            val ourTimeWindow: TimeWindow = TimeWindow.between(Instant.MIN, Instant.MAX)
            val ourAfter: TimeWindow = TimeWindow.fromOnly(Instant.MIN)
            val ourBefore: TimeWindow = TimeWindow.untilOnly(Instant.MAX)

            /**-----------------------
             * TRANSACTION BUILDING *
            -----------------------**/
            progressTracker.currentStep = TX_BUILDING

            // There are two types of transaction (notary-change and general),
            // and therefore two types of transaction builder:
            val notaryChangeTxBuilder: TransactionBuilder = TransactionBuilder(NotaryChange, specificNotary)
            val regTxBuilder: TransactionBuilder = TransactionBuilder(General, specificNotary)

            // We add items to the transaction builder using ``TransactionBuilder.withItems``:
            regTxBuilder.withItems(
                    // Inputs, as ``StateRef``s that reference to the outputs of previous transactions
                    ourStateRef,
                    // Outputs, as ``ContractState``s
                    ourOutput,
                    // Commands, as ``Command``s
                    ourCommand
            )

            // We can also add items using methods for the individual components:
            regTxBuilder.addInputState(ourStateAndRef)
            regTxBuilder.addOutputState(ourOutput)
            regTxBuilder.addCommand(ourCommand)
            regTxBuilder.addAttachment(ourAttachment)
            regTxBuilder.addTimeWindow(ourTimeWindow)

            /**----------------------
             * TRANSACTION SIGNING *
            ----------------------**/
            progressTracker.currentStep = TX_SIGNING

            // We finalise the transaction by signing it, converting it into a
            // ``SignedTransaction``.
            val onceSignedTx: SignedTransaction = serviceHub.signInitialTransaction(regTxBuilder)

            // If instead this was a ``SignedTransaction`` that we'd received
            // from a counterparty and we needed to sign it, we would add our
            // signature using:
            val twiceSignedTx: SignedTransaction = serviceHub.addSignature(onceSignedTx, dummyPubKey)

            /**---------------------------
             * TRANSACTION VERIFICATION *
            ---------------------------**/
            progressTracker.currentStep = TX_VERIFICATION

            // Verifying a transaction will also verify every transaction in
            // the transaction's dependency chain. So if this was a
            // transaction we'd received from a counterparty and it had any
            // dependencies, we'd need to download all of these dependencies
            // using``ResolveTransactionsFlow`` before verifying it.
            subFlow(ResolveTransactionsFlow(twiceSignedTx, counterparty))

            // We verify a transaction using the following one-liner:
            twiceSignedTx.tx.toLedgerTransaction(serviceHub).verify()

            // Let's break that down...

            // A ``SignedTransaction`` is a pairing of a ``WireTransaction``
            // with signatures over this ``WireTransaction``. We don't verify
            // a signed transaction per se, but rather the ``WireTransaction``
            // it contains.
            val wireTx: WireTransaction = twiceSignedTx.tx
            // Before we can verify the transaction, we need the
            // ``ServiceHub`` to use our node's local storage to resolve the
            // transaction's inputs and attachments into actual objects,
            // rather than just references. We do this by converting the
            // ``WireTransaction`` into a ``LedgerTransaction``.
            val ledgerTx: LedgerTransaction = wireTx.toLedgerTransaction(serviceHub)
            // We can now verify the transaction.
            ledgerTx.verify()

            // We'll often want to perform our own additional verification
            // too. This can be whatever we see fit.
            val outputState: DummyState = wireTx.outputs.single().data as DummyState
            assert(outputState.magicNumber == 777)

            // TODO: Show throwing a FlowException when unhappy (is that good practice?)

            /**-----------------------
             * GATHERING SIGNATURES *
            -----------------------**/
            progressTracker.currentStep = SIGS_GATHERING

            // The list of parties who need to sign a transaction is dictated
            // by the transaction's commands. Once we've signed a transaction
            // ourselves, we can automatically gather the signatures of the
            // other required signers using ``CollectSignaturesFlow``.
            // The responder flow will need to call ``SignTransactionFlow``.
            val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(twiceSignedTx, SIGS_GATHERING.childProgressTracker()))

            /**-----------------------------
             * FINALISING THE TRANSACTION *
            -----------------------------**/
            progressTracker.currentStep = FINALISATION

            // We notarise the transaction and get it recorded in the vault of
            // all the participants of all the transaction's states.
            val notarisedTx1: SignedTransaction = subFlow(FinalityFlow(fullySignedTx, FINALISATION.childProgressTracker())).single()
            // We can also choose to send it to additional parties who aren't one
            // of the state's participants.
            val additionalParties: Set<Party> = setOf(dummyRegulator, dummyBank)
            val notarisedTx2: SignedTransaction = subFlow(FinalityFlow(listOf(fullySignedTx), additionalParties, FINALISATION.childProgressTracker())).single()
        }
    }

    // ``ResponderFlow`` is our second flow, and will communicate with
    // ``InitiatorFlow``.
    // We mark ``ResponderFlow`` as an ``InitiatedByFlow``, meaning that it
    // can only be started in response to a message from its initiating flow.
    // That's ``InitiatorFlow`` in this case.
    // Each node also has several flow pairs registered by default - see
    // ``AbstractNode.installCoreFlows``.
    @InitiatedBy(InitiatorFlow::class)
    class ResponderFlow(val counterparty: Party) : FlowLogic<Unit>() {

        companion object {
            object RECEIVING_AND_SENDING_DATA : Step("Sending data between parties.")
            object SIGNING : Step("Responding to CollectSignaturesFlow.")
            object FINALISATION : Step("Finalising a transaction.")

            fun tracker() = ProgressTracker(
                    RECEIVING_AND_SENDING_DATA,
                    SIGNING,
                    FINALISATION
            )
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call() {
            // The ``ResponderFlow` has all the same APIs available. It looks
            // up network information, sends and receives data, and constructs
            // transactions in exactly the same way.

            /**-----------------------------
             * SENDING AND RECEIVING DATA *
            -----------------------------**/
            progressTracker.currentStep = RECEIVING_AND_SENDING_DATA

            // We need to respond to the messages sent by the initiator:
            // 1. They sent us an ``Any`` instance
            // 2. They waited to receive an ``Integer`` instance back
            // 3. They sent a ``String`` instance and waited to receive a
            //    ``Boolean`` instance back
            // Our side of the flow must mirror these calls.
            val any: Any = receive<Any>(counterparty).unwrap { data -> data }
            val string: String = sendAndReceive<String>(counterparty, 99).unwrap { data -> data }
            send(counterparty, true)

            /**----------------------------------------
             * RESPONDING TO COLLECT_SIGNATURES_FLOW *
            ----------------------------------------**/
            progressTracker.currentStep = SIGNING

            // The responder will often need to respond to a call to
            // ``CollectSignaturesFlow``. It does so my invoking its own
            // ``SignTransactionFlow`` subclass.
            val signTransactionFlow: SignTransactionFlow = object : SignTransactionFlow(counterparty) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // Any additional checking we see fit...
                    val outputState = stx.tx.outputs.single().data as DummyState
                    assert(outputState.magicNumber == 777)
                }
            }

            subFlow(signTransactionFlow)

            /**-----------------------------
             * FINALISING THE TRANSACTION *
            -----------------------------**/
            progressTracker.currentStep = FINALISATION

            // Nothing to do here! As long as some other party calls
            // ``FinalityFlow``, the recording of the transaction on our node
            // we be handled automatically.
        }
    }
}