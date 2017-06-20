package net.corda.docs;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.*;
import net.corda.core.contracts.TransactionType.General;
import net.corda.core.contracts.TransactionType.NotaryChange;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.NetworkMapCache;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.transactions.WireTransaction;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import net.corda.core.utilities.UntrustworthyData;
import net.corda.flows.CollectSignaturesFlow;
import net.corda.flows.FinalityFlow;
import net.corda.flows.ResolveTransactionsFlow;
import net.corda.flows.SignTransactionFlow;
import org.bouncycastle.asn1.x500.X500Name;

import java.time.Instant;
import java.util.Set;

import static net.corda.core.contracts.ContractsDSL.requireThat;
import static net.corda.core.utilities.TestConstants.*;

// We group our two flows inside a singleton object to indicate that they work
// together.
public class FlowCookbookJava {
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
    public static class InitiatorFlow extends FlowLogic<Void> {

        private final boolean arg1;
        private final int arg2;
        private final Party counterparty;

        public InitiatorFlow(boolean arg1, int arg2, Party counterparty) {
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.counterparty = counterparty;
        }

        /*----------------------------------
         * WIRING UP THE PROGRESS TRACKER *
        ----------------------------------*/
        private static final Step ID_OTHER_NODES = new Step("Identifying other nodes on the network.");
        private static final Step SENDING_AND_RECEIVING_DATA = new Step("Sending data between parties.");
        private static final Step EXTRACTING_VAULT_STATES = new Step("Extracting states from the vault.");
        private static final Step OTHER_TX_COMPONENTS = new Step("Gathering a transaction's other components.");
        private static final Step TX_BUILDING = new Step("Building a transaction.");
        private static final Step TX_SIGNING = new Step("Signing a transaction.");
        private static final Step TX_VERIFICATION = new Step("Verifying a transaction.");
        private static final Step SIGS_GATHERING = new Step("Gathering a transaction's signatures.") {
            @Override public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private static final Step FINALISATION = new Step("Finalising a transaction.") {
            @Override public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                ID_OTHER_NODES,
                SENDING_AND_RECEIVING_DATA,
                EXTRACTING_VAULT_STATES,
                OTHER_TX_COMPONENTS,
                TX_BUILDING,
                TX_SIGNING,
                TX_VERIFICATION,
                SIGS_GATHERING,
                FINALISATION
        );

        @Suspendable
        @Override
        public Void call() throws FlowException {
            /*---------------------------
             * IDENTIFYING OTHER NODES *
            ---------------------------*/
            progressTracker.setCurrentStep(ID_OTHER_NODES);

            // A transaction generally needs a notary:
            //   - To prevent double-spends if the transaction has inputs
            //   - To serve as a timestamping authority if the transaction has a time-window
            // We retrieve the notary from the network map.
            final NetworkMapCache networkMap = getServiceHub().getNetworkMapCache();
            Party specificNotary = networkMap.getNotary(new X500Name("CN=Notary Service,O=R3,OU=corda,L=London,C=UK"));
            Party anyNotary = networkMap.getAnyNotary(null);
            Party firstNotary = networkMap.getNotaryNodes().get(0).getNotaryIdentity();

            // We may also need to identify a specific counterparty.
            // Again, we do so using the network map.
            Party namedCounterparty = networkMap.getNodeByLegalName(new X500Name("CN=NodeA,O=NodeA,L=London,C=UK")).getLegalIdentity();
            Party keyedCounterparty = networkMap.getNodeByLegalIdentityKey(getDUMMY_PUBKEY_1()).getLegalIdentity();
            Party firstCounterparty = networkMap.getPartyNodes().get(0).getLegalIdentity();

            /*------------------------------
             * SENDING AND RECEIVING DATA *
            ------------------------------*/
            progressTracker.setCurrentStep(SENDING_AND_RECEIVING_DATA);

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
            send(getDUMMY_BANK_A(), new Object());

            // We can wait to receive arbitrary data of a specific type from a
            // counterparty. Again, this implies a corresponding ``send`` call
            // in the counterparty's flow.
            UntrustworthyData<Object> data1 = receive(Object.class, getDUMMY_BANK_B());
            // We receive the data wrapped in an ``UntrustworthyData``
            // instance, which we must unwrap using a lambda.
            Object any1 = data1.unwrap(data -> {
                // Perform checking on the object received.
                // T O D O: Check the received object.
                // Return the object.
                return data;
            });

            // We can also send data to a counterparty and wait to receive
            // data of a specific type back.
            UntrustworthyData<Object> data2 = sendAndReceive(Object.class, getDUMMY_BANK_C(), new Object());
            Object any2 = data2.unwrap(data -> {
                // Perform checking on the object received.
                // T O D O: Check the received object.
                // Return the object.
                return data;
            });

            /*------------------------------------
             * EXTRACTING STATES FROM THE VAULT *
            ------------------------------------*/
            progressTracker.setCurrentStep(EXTRACTING_VAULT_STATES);

            // TODO: Retrieving from the vault

            // Input states are identified using ``StateRef`` instances,
            // which pair the hash of the transaction that generated the state
            // with the state's index in the outputs of that transaction.
            StateRef DUMMY_STATE_REF = new StateRef(SecureHash.sha256("DummyTransactionHash"), 0);
            // A ``StateAndRef`` pairs a ``StateRef`` with the state it points to.
            StateAndRef DUMMY_STATE_AND_REF = getServiceHub().toStateAndRef(DUMMY_STATE_REF);

            /*------------------------------------------
             * GATHERING OTHER TRANSACTION COMPONENTS *
            ------------------------------------------*/
            progressTracker.setCurrentStep(OTHER_TX_COMPONENTS);

            // Output states are constructed from scratch.
            DummyState DUMMY_OUTPUT = new DummyState();

            // Commands pair a ``CommandData`` type with a list of signers.
            Command DUMMY_COMMAND = new Command(new DummyContract.Commands.Create(), ImmutableList.of(getDUMMY_PUBKEY_1()));

            // Attachments are identified by their hash.
            // The attachment with the corresponding hash must have been
            // uploaded ahead of time via the node's RPC interface.
            SecureHash DUMMY_ATTACHMENT = SecureHash.sha256("DummyAttachment");

            // Timewindows can have a start and end time, or be open at either end.
            TimeWindow DUMMY_TIME_WINDOW = TimeWindow.between(Instant.MIN, Instant.MAX);
            TimeWindow DUMMY_AFTER = TimeWindow.fromOnly(Instant.MIN);
            TimeWindow DUMMY_BEFORE = TimeWindow.untilOnly(Instant.MAX);

            /*------------------------
             * TRANSACTION BUILDING *
            ------------------------*/
            progressTracker.setCurrentStep(TX_BUILDING);

            // There are two types of transaction (notary-change and general),
            // and therefore two types of transaction builder:
            TransactionBuilder notaryChangeTxBuilder = new TransactionBuilder(NotaryChange.INSTANCE, getDUMMY_NOTARY());
            TransactionBuilder regTxBuilder = new TransactionBuilder(General.INSTANCE, getDUMMY_NOTARY());

            // We add items to the transaction builder using ``TransactionBuilder.withItems``:
            regTxBuilder.withItems(
                    // Inputs, as ``StateRef``s that reference to the outputs of previous transactions
                    DUMMY_STATE_REF,
                    // Outputs, as ``ContractState``s
                    DUMMY_OUTPUT,
                    // Commands, as ``Command``s
                    DUMMY_COMMAND
            );

            // We can also add items using methods for the individual components:
            regTxBuilder.addInputState(DUMMY_STATE_AND_REF);
            regTxBuilder.addOutputState(DUMMY_OUTPUT);
            regTxBuilder.addCommand(DUMMY_COMMAND);
            regTxBuilder.addAttachment(DUMMY_ATTACHMENT);
            regTxBuilder.addTimeWindow(DUMMY_TIME_WINDOW);

            /*-----------------------
             * TRANSACTION SIGNING *
            -----------------------*/
            progressTracker.setCurrentStep(TX_SIGNING);

            // We finalise the transaction by signing it,
            // converting it into a ``SignedTransaction``.
            SignedTransaction onceSignedTx = getServiceHub().signInitialTransaction(regTxBuilder);

            // Parties can then add additional signatures as well.
            SignedTransaction twiceSignedTx = getServiceHub().addSignature(onceSignedTx, getDUMMY_PUBKEY_1());

            /*----------------------------
             * TRANSACTION VERIFICATION *
            ----------------------------*/
            progressTracker.setCurrentStep(TX_VERIFICATION);

            // Verifying a transaction will also verify every transaction in
            // the transaction's dependency chain. So if a counterparty sends
            // us a transaction, we need to download all of its dependencies
            // using``ResolveTransactionsFlow`` before verifying it.
            subFlow(new ResolveTransactionsFlow(twiceSignedTx, counterparty));

            // We verify a transaction using the following one-liner:
            twiceSignedTx.getTx().toLedgerTransaction(getServiceHub()).verify();

            // Let's break that down...

            // A ``SignedTransaction`` is a pairing of a ``WireTransaction``
            // with signatures over this ``WireTransaction``.
            // We don't verify a signed transaction per se, but rather the ``WireTransaction`` it contains.
            WireTransaction wireTx = twiceSignedTx.getTx();
            // Before we can verify the transaction, we have to resolve its inputs and attachments
            // into actual objects, rather than just references.
            // We achieve this by converting the ``WireTransaction`` into a ``LedgerTransaction``.
            LedgerTransaction ledgerTx = wireTx.toLedgerTransaction(getServiceHub());
            // We can now verify the transaction.
            ledgerTx.verify();

            // We'll often want to perform our own additional verification
            // too. This can be whatever we see fit.
            DummyState outputState = (DummyState) wireTx.getOutputs().get(0).getData();
            assert(outputState.getMagicNumber() == 777);

            /*------------------------
             * GATHERING SIGNATURES *
            ------------------------*/
            progressTracker.setCurrentStep(SIGS_GATHERING);

            // The list of parties who need to sign a transaction is dictated
            // by the transaction's commands. Once we've signed a transaction
            // ourselves, we can automatically gather the signatures of the
            // other required signers using ``CollectSignaturesFlow``.
            // The responder flow will need to call ``SignTransactionFlow``.
            SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(twiceSignedTx, SIGS_GATHERING.childProgressTracker()));

            /*------------------------------
             * FINALISING THE TRANSACTION *
            ------------------------------*/
            progressTracker.setCurrentStep(FINALISATION);

            // We notarise the transaction and get it recorded in the vault of
            // all the participants of all the transaction's states.
            SignedTransaction notarisedTx1 = subFlow(new FinalityFlow(fullySignedTx, FINALISATION.childProgressTracker())).get(0);
            // We can also choose to send it to additional parties who aren't one
            // of the state's participants.
            Set<Party> additionalParties = ImmutableSet.of(getDUMMY_REGULATOR(), getDUMMY_BANK_A());
            SignedTransaction notarisedTx2 = subFlow(new FinalityFlow(ImmutableList.of(fullySignedTx), additionalParties, FINALISATION.childProgressTracker())).get(0);

            return null;
        }
    }

    // ``ResponderFlow`` is our second flow, and will communicate with
    // ``InitiatorFlow``.
    // We mark ``ResponderFlow`` as an ``InitiatedByFlow``, meaning that it
    // can only be started in response to a message from its initiating flow.
    // That's ``InitiatorFlow`` in this case.
    // Each node also has several flow pairs registered by default - see
    // ``AbstractNode.installCoreFlows``.
    @InitiatedBy(InitiatorFlow.class)
    public static class ResponderFlow extends FlowLogic<Void> {

        private final Party otherParty;

        public ResponderFlow(Party otherParty) {
            this.otherParty = otherParty;
        }

        private static final Step SIGNING = new Step("Responding to CollectSignaturesFlow.");
        private static final Step FINALISATION = new Step("Finalising a transaction.");

        private final ProgressTracker progressTracker = new ProgressTracker(
                SIGNING,
                FINALISATION
        );

        @Suspendable
        @Override
        public Void call() throws FlowException {
            // The ``ResponderFlow` has all the same APIs available. It looks
            // up network information, sends and receives data, and constructs
            // transactions in exactly the same way.

            /*-----------------------------------------
             * RESPONDING TO COLLECT_SIGNATURES_FLOW *
            -----------------------------------------*/
            progressTracker.setCurrentStep(SIGNING);

            // The responder will often need to respond to a call to
            // ``CollectSignaturesFlow``. It does so my invoking its own
            // ``SignTransactionFlow`` subclass.
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(Party otherParty, ProgressTracker progressTracker) {
                    super(otherParty, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(require -> {
                        // Any additional checking we see fit...
                        DummyState outputState = (DummyState) stx.getTx().getOutputs().get(0).getData();
                        assert (outputState.getMagicNumber() == 777);
                        return null;
                    });
                }
            }

            subFlow(new SignTxFlow(otherParty, SignTransactionFlow.Companion.tracker()));

            /*------------------------------
             * FINALISING THE TRANSACTION *
            ------------------------------*/
            progressTracker.setCurrentStep(FINALISATION);

            // Nothing to do here! As long as some other party calls
            // ``FinalityFlow``, the recording of the transaction on our node
            // we be handled automatically.

            return null;
        }
    }
}