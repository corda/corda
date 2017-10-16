package net.corda.docs.java.tutorial.helloworld;

// DOCSTART 01
import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

@InitiatingFlow
@StartableByRPC
public class IOUFlow extends FlowLogic<Void> {
    private final Integer iouValue;
    private final Party otherParty;

    /**
     * The progress tracker provides checkpoints indicating the progress of the flow to observers.
     */
    private final ProgressTracker progressTracker = new ProgressTracker();

    public IOUFlow(Integer iouValue, Party otherParty) {
        this.iouValue = iouValue;
        this.otherParty = otherParty;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    @Override
    public Void call() throws FlowException {
        // We retrieve the notary identity from the network map.
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        // We create a transaction builder.
        final TransactionBuilder txBuilder = new TransactionBuilder();
        txBuilder.setNotary(notary);

        // We create the transaction components.
        IOUState outputState = new IOUState(iouValue, getOurIdentity(), otherParty);
        String outputContract = IOUContract.class.getName();
        StateAndContract outputContractAndState = new StateAndContract(outputState, outputContract);
        Command cmd = new Command<>(new IOUContract.Create(), getOurIdentity().getOwningKey());

        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd);

        // Verifying the transaction.
        txBuilder.verify(getServiceHub());

        // Signing the transaction.
        final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        // Finalising the transaction.
        subFlow(new FinalityFlow(signedTx));

        return null;
    }
}
// DOCEND 01