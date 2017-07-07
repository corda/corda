package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.contracts.TransactionType.General
import net.corda.core.contracts.TransactionType.NotaryChange
import net.corda.core.contracts.testing.DummyContract
import net.corda.core.contracts.testing.DummyState
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.ServiceType
import net.corda.core.node.services.Vault.Page
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.testing.DUMMY_PUBKEY_1
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.ResolveTransactionsFlow
import net.corda.flows.SignTransactionFlow
import org.bouncycastle.asn1.x500.X500Name
import java.security.PublicKey
import java.time.Duration
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
        // Giving our flow a progress tracker allows us to see the flow's
        // progress visually in our node's CRaSH shell.
        // DOCSTART 17
        companion object {
            object ID_OTHER_NODES : Step("Identifying other nodes on the network.")
            object SENDING_AND_RECEIVING_DATA : Step("Sending data between parties.")
            object EXTRACTING_VAULT_STATES : Step("Extracting states from the vault.")
            object OTHER_TX_COMPONENTS : Step("Gathering a transaction's other components.")
            object TX_BUILDING : Step("Building a transaction.")
            object TX_SIGNING : Step("Signing a transaction.")
            object TX_VERIFICATION : Step("Verifying a transaction.")
            object SIGS_GATHERING : Step("Gathering a transaction's signatures.") {
                // Wiring up a child progress tracker allows us to see the
                // subflow's progress steps in our flow's progress tracker.
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object VERIFYING_SIGS : Step("Verifying a transaction's signatures.")
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
                    VERIFYING_SIGS,
                    FINALISATION
            )
        }
        // DOCEND 17

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call() {
            // We'll be using a dummy public key for demonstration purposes.
            // These are built in to Corda, and are generally used for writing
            // tests.
            val dummyPubKey: PublicKey = DUMMY_PUBKEY_1

            /**--------------------------
             * IDENTIFYING OTHER NODES *
            --------------------------**/
            // DOCSTART 18
            progressTracker.currentStep = ID_OTHER_NODES
            // DOCEND 18

            // A transaction generally needs a notary:
            //   - To prevent double-spends if the transaction has inputs
            //   - To serve as a timestamping authority if the transaction has a time-window
            // We retrieve the notary from the network map.
            // DOCSTART 1
            val specificNotary: Party? = serviceHub.networkMapCache.getNotary(X500Name("CN=Notary Service,O=R3,OU=corda,L=London,C=UK"))
            val anyNotary: Party? = serviceHub.networkMapCache.getAnyNotary()
            // Unlike the first two methods, ``getNotaryNodes`` returns a
            // ``List<NodeInfo>``. We have to extract the notary identity of
            // the node we want.
            val firstNotary: Party = serviceHub.networkMapCache.notaryNodes[0].notaryIdentity
            // DOCEND 1

            // We may also need to identify a specific counterparty. Again, we
            // do so using the network map.
            // DOCSTART 2
            val namedCounterparty: Party? = serviceHub.networkMapCache.getNodeByLegalName(X500Name("CN=NodeA,O=NodeA,L=London,C=UK"))?.legalIdentity
            val keyedCounterparty: Party? = serviceHub.networkMapCache.getNodeByLegalIdentityKey(dummyPubKey)?.legalIdentity
            val firstCounterparty: Party = serviceHub.networkMapCache.partyNodes[0].legalIdentity
            // DOCEND 2

            // Finally, we can use the map to identify nodes providing a
            // specific service (e.g. a regulator or an oracle).
            // DOCSTART 3
            val regulator: Party = serviceHub.networkMapCache.getNodesWithService(ServiceType.regulator)[0].legalIdentity
            // DOCEND 3

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
            // DOCSTART 4
            send(counterparty, Any())
            // DOCEND 4

            // We can wait to receive arbitrary data of a specific type from a
            // counterparty. Again, this implies a corresponding ``send`` call
            // in the counterparty's flow. A few scenarios:
            // - We never receive a message back. In the current design, the
            //   flow is paused until the node's owner kills the flow.
            // - Instead of sending a message back, the counterparty throws a
            //   ``FlowException``. This exception is propagated back to us,
            //   and we can use the error message to establish what happened.
            // - We receive a message back, but it's of the wrong type. In
            //   this case, a ``FlowException`` is thrown.
            // - We receive back a message of the correct type. All is good.
            //
            // Upon calling ``receive()`` (or ``sendAndReceive()``), the
            // ``FlowLogic`` is suspended until it receives a response.
            //
            // We receive the data wrapped in an ``UntrustworthyData``
            // instance. This is a reminder that the data we receive may not
            // be what it appears to be! We must unwrap the
            // ``UntrustworthyData`` using a lambda.
            // DOCSTART 5
            val packet1: UntrustworthyData<Int> = receive<Int>(counterparty)
            val int: Int = packet1.unwrap { data ->
                // Perform checking on the object received.
                // T O D O: Check the received object.
                // Return the object.
                data
            }
            // DOCEND 5

            // We can also use a single call to send data to a counterparty
            // and wait to receive data of a specific type back. The type of
            // data sent doesn't need to match the type of the data received
            // back.
            // DOCSTART 7
            val packet2: UntrustworthyData<Boolean> = sendAndReceive<Boolean>(counterparty, "You can send and receive any class!")
            val boolean: Boolean = packet2.unwrap { data ->
                // Perform checking on the object received.
                // T O D O: Check the received object.
                // Return the object.
                data
            }
            // DOCEND 7

            // We're not limited to sending to and receiving from a single
            // counterparty. A flow can send messages to as many parties as it
            // likes, and each party can invoke a different response flow.
            // DOCSTART 6
            send(regulator, Any())
            val packet3: UntrustworthyData<Any> = receive<Any>(regulator)
            // DOCEND 6

            /**-----------------------------------
             * EXTRACTING STATES FROM THE VAULT *
            -----------------------------------**/
            progressTracker.currentStep = EXTRACTING_VAULT_STATES

            // Let's assume there are already some ``DummyState``s in our
            // node's vault, stored there as a result of running past flows,
            // and we want to consume them in a transaction. There are many
            // ways to extract these states from our vault.

            // For example, we would extract any unconsumed ``DummyState``s
            // from our vault as follows:
            val criteria: VaultQueryCriteria = VaultQueryCriteria() // default is UNCONSUMED
            val results: Page<DummyState> = serviceHub.vaultQueryService.queryBy<DummyState>(criteria)
            val dummyStates: List<StateAndRef<DummyState>> = results.states

            // For a full list of the available ways of extracting states from
            // the vault, see the Vault Query docs page.

            // When building a transaction, input states are passed in as
            // ``StateRef`` instances, which pair the hash of the transaction
            // that generated the state with the state's index in the outputs
            // of that transaction.
            // DOCSTART 20
            val ourStateRef: StateRef = StateRef(SecureHash.sha256("DummyTransactionHash"), 0)
            // DOCEND 20
            // A ``StateAndRef`` pairs a ``StateRef`` with the state it points to.
            // DOCSTART 21
            val ourStateAndRef: StateAndRef<DummyState> = serviceHub.toStateAndRef<DummyState>(ourStateRef)
            // DOCEND 21

            /**-----------------------------------------
             * GATHERING OTHER TRANSACTION COMPONENTS *
            -----------------------------------------**/
            progressTracker.currentStep = OTHER_TX_COMPONENTS

            // Output states are constructed from scratch.
            // DOCSTART 22
            val ourOutput: DummyState = DummyState()
            // DOCEND 22
            // Or as copies of other states with some properties changed.
            // DOCSTART 23
            val ourOtherOutput: DummyState = ourOutput.copy(magicNumber = 77)
            // DOCEND 23

            // Commands pair a ``CommandData`` instance with a list of
            // public keys. To be valid, the transaction requires a signature
            // matching every public key in all of the transaction's commands.
            // DOCSTART 24
            val commandData: CommandData = DummyContract.Commands.Create()
            val ourPubKey: PublicKey = serviceHub.legalIdentityKey
            val counterpartyPubKey: PublicKey = counterparty.owningKey
            val requiredSigners: List<PublicKey> = listOf(ourPubKey, counterpartyPubKey)
            val ourCommand: Command = Command(commandData, requiredSigners)
            // DOCEND 24

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
            // DOCSTART 25
            val ourAttachment: SecureHash = SecureHash.sha256("DummyAttachment")
            // DOCEND 25

            // Time windows can have a start and end time, or be open at either end.
            // DOCSTART 26
            val ourTimeWindow: TimeWindow = TimeWindow.between(Instant.MIN, Instant.MAX)
            val ourAfter: TimeWindow = TimeWindow.fromOnly(Instant.MIN)
            val ourBefore: TimeWindow = TimeWindow.untilOnly(Instant.MAX)
            // DOCEND 26

            // We can also define a time window as an ``Instant`` +/- a time
            // tolerance (e.g. 30 seconds):
            // DOCSTART 42
            val ourTimeWindow2: TimeWindow = TimeWindow.withTolerance(Instant.now(), Duration.ofSeconds(30))
            // DOCEND 42
            // Or as a start-time plus a duration:
            // DOCSTART 43
            val ourTimeWindow3: TimeWindow = TimeWindow.fromStartAndDuration(Instant.now(), Duration.ofSeconds(30))
            // DOCEND 43

            /**-----------------------
             * TRANSACTION BUILDING *
            -----------------------**/
            progressTracker.currentStep = TX_BUILDING

            // There are two types of transaction (notary-change and general),
            // and therefore two types of transaction builder:
            // DOCSTART 19
            val notaryChangeTxBuilder: TransactionBuilder = TransactionBuilder(NotaryChange, specificNotary)
            val regTxBuilder: TransactionBuilder = TransactionBuilder(General, specificNotary)
            // DOCEND 19

            // We add items to the transaction builder using ``TransactionBuilder.withItems``:
            // DOCSTART 27
            regTxBuilder.withItems(
                    // Inputs, as ``StateRef``s that reference the outputs of previous transactions
                    ourStateAndRef,
                    // Outputs, as ``ContractState``s
                    ourOutput,
                    // Commands, as ``Command``s
                    ourCommand
            )
            // DOCEND 27

            // We can also add items using methods for the individual components:
            // DOCSTART 28
            regTxBuilder.addInputState(ourStateAndRef)
            regTxBuilder.addOutputState(ourOutput)
            regTxBuilder.addCommand(ourCommand)
            regTxBuilder.addAttachment(ourAttachment)
            // DOCEND 28

            // There are several ways of setting the transaction's time-window.
            // We can set a time-window directly:
            // DOCSTART 44
            regTxBuilder.timeWindow = ourTimeWindow
            // DOCEND 44
            // Or as a start time plus a duration (e.g. 45 seconds):
            // DOCSTART 45
            regTxBuilder.setTimeWindow(Instant.now(), Duration.ofSeconds(45))
            // DOCEND 45

            /**----------------------
             * TRANSACTION SIGNING *
            ----------------------**/
            progressTracker.currentStep = TX_SIGNING

            // We finalise the transaction by signing it, converting it into a
            // ``SignedTransaction``.
            // DOCSTART 29
            val onceSignedTx: SignedTransaction = serviceHub.signInitialTransaction(regTxBuilder)
            // DOCEND 29
            // We can also sign the transaction using a different public key:
            // DOCSTART 30
            val otherKey: PublicKey = serviceHub.keyManagementService.freshKey()
            val onceSignedTx2: SignedTransaction = serviceHub.signInitialTransaction(regTxBuilder, otherKey)
            // DOCEND 30

            // If instead this was a ``SignedTransaction`` that we'd received
            // from a counterparty and we needed to sign it, we would add our
            // signature using:
            // DOCSTART 38
            val twiceSignedTx: SignedTransaction = serviceHub.addSignature(onceSignedTx)
            // DOCEND 38
            // Or, if we wanted to use a different public key:
            val otherKey2: PublicKey = serviceHub.keyManagementService.freshKey()
            // DOCSTART 39
            val twiceSignedTx2: SignedTransaction = serviceHub.addSignature(onceSignedTx, otherKey2)
            // DOCEND 39

            // We can also generate a signature over the transaction without
            // adding it to the transaction itself. We may do this when 
            // sending just the signature in a flow instead of returning the 
            // entire transaction with our signature. This way, the receiving 
            // node does not need to check we haven't changed anything in the 
            // transaction.
            // DOCSTART 40
            val sig: DigitalSignature.WithKey = serviceHub.createSignature(onceSignedTx)
            // DOCEND 40
            // And again, if we wanted to use a different public key:
            // DOCSTART 41
            val sig2: DigitalSignature.WithKey = serviceHub.createSignature(onceSignedTx, otherKey2)
            // DOCEND 41

            // In practice, however, the process of gathering every signature
            // but the first can be automated using ``CollectSignaturesFlow``.
            // See the "Gathering Signatures" section below.

            /**---------------------------
             * TRANSACTION VERIFICATION *
            ---------------------------**/
            progressTracker.currentStep = TX_VERIFICATION

            // Verifying a transaction will also verify every transaction in
            // the transaction's dependency chain. So if this was a
            // transaction we'd received from a counterparty and it had any
            // dependencies, we'd need to download all of these dependencies
            // using``ResolveTransactionsFlow`` before verifying it.
            // DOCSTART 13
            subFlow(ResolveTransactionsFlow(twiceSignedTx, counterparty))
            // DOCEND 13

            // We can also resolve a `StateRef` dependency chain.
            // DOCSTART 14
            subFlow(ResolveTransactionsFlow(setOf(ourStateRef.txhash), counterparty))
            // DOCEND 14

            // A ``SignedTransaction`` is a pairing of a ``WireTransaction``
            // with signatures over this ``WireTransaction``. We don't verify
            // a signed transaction per se, but rather the ``WireTransaction``
            // it contains.
            // DOCSTART 31
            val wireTx: WireTransaction = twiceSignedTx.tx
            // DOCEND 31
            // Before we can verify the transaction, we need the
            // ``ServiceHub`` to use our node's local storage to resolve the
            // transaction's inputs and attachments into actual objects,
            // rather than just references. We do this by converting the
            // ``WireTransaction`` into a ``LedgerTransaction``.
            // DOCSTART 32
            val ledgerTx: LedgerTransaction = wireTx.toLedgerTransaction(serviceHub)
            // DOCEND 32
            // We can now verify the transaction.
            // DOCSTART 33
            ledgerTx.verify()
            // DOCEND 33

            // We'll often want to perform our own additional verification
            // too. Just because a transaction is valid based on the contract
            // rules and requires our signature doesn't mean we have to
            // sign it! We need to make sure the transaction represents an
            // agreement we actually want to enter into.
            // DOCSTART 34
            val outputState: DummyState = wireTx.outputs.single().data as DummyState
            if (outputState.magicNumber == 777) {
                // ``FlowException`` is a special exception type. It will be
                // propagated back to any counterparty flows waiting for a
                // message from this flow, notifying them that the flow has
                // failed.
                throw FlowException("We expected a magic number of 777.")
            }
            // DOCEND 34

            // Of course, if you are not a required signer on the transaction,
            // you have no power to decide whether it is valid or not. If it
            // requires signatures from all the required signers and is
            // contractually valid, it's a valid ledger update.

            /**-----------------------
             * GATHERING SIGNATURES *
            -----------------------**/
            progressTracker.currentStep = SIGS_GATHERING

            // The list of parties who need to sign a transaction is dictated
            // by the transaction's commands. Once we've signed a transaction
            // ourselves, we can automatically gather the signatures of the
            // other required signers using ``CollectSignaturesFlow``.
            // The responder flow will need to call ``SignTransactionFlow``.
            // DOCSTART 15
            val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(twiceSignedTx, SIGS_GATHERING.childProgressTracker()))
            // DOCEND 15

            /**-----------------------
             * VERIFYING SIGNATURES *
            -----------------------**/
            progressTracker.currentStep = VERIFYING_SIGS

            // We can verify that a transaction has all the required
            // signatures, and that they're all valid, by running:
            // DOCSTART 35
            fullySignedTx.verifySignatures()
            // DOCEND 35

            // If the transaction is only partially signed, we have to pass in
            // a list of the public keys corresponding to the missing
            // signatures, explicitly telling the system not to check them.
            // DOCSTART 36
            onceSignedTx.verifySignatures(counterpartyPubKey)
            // DOCEND 36

            // We can also choose to only check the signatures that are
            // present. BE VERY CAREFUL - this function provides no guarantees
            // that the signatures are correct, or that none are missing.
            // DOCSTART 37
            twiceSignedTx.checkSignaturesAreValid()
            // DOCEND 37

            /**-----------------------------
             * FINALISING THE TRANSACTION *
            -----------------------------**/
            progressTracker.currentStep = FINALISATION

            // We notarise the transaction and get it recorded in the vault of
            // the participants of all the transaction's states.
            // DOCSTART 9
            val notarisedTx1: SignedTransaction = subFlow(FinalityFlow(fullySignedTx, FINALISATION.childProgressTracker())).single()
            // DOCEND 9
            // We can also choose to send it to additional parties who aren't one
            // of the state's participants.
            // DOCSTART 10
            val additionalParties: Set<Party> = setOf(regulator)
            val notarisedTx2: SignedTransaction = subFlow(FinalityFlow(listOf(fullySignedTx), additionalParties, FINALISATION.childProgressTracker())).single()
            // DOCEND 10
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
            // DOCSTART 8
            val any: Any = receive<Any>(counterparty).unwrap { data -> data }
            val string: String = sendAndReceive<String>(counterparty, 99).unwrap { data -> data }
            send(counterparty, true)
            // DOCEND 8

            /**----------------------------------------
             * RESPONDING TO COLLECT_SIGNATURES_FLOW *
            ----------------------------------------**/
            progressTracker.currentStep = SIGNING

            // The responder will often need to respond to a call to
            // ``CollectSignaturesFlow``. It does so my invoking its own
            // ``SignTransactionFlow`` subclass.
            // DOCSTART 16
            val signTransactionFlow: SignTransactionFlow = object : SignTransactionFlow(counterparty) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // Any additional checking we see fit...
                    val outputState = stx.tx.outputs.single().data as DummyState
                    assert(outputState.magicNumber == 777)
                }
            }

            subFlow(signTransactionFlow)
            // DOCEND 16

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
