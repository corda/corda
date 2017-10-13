package net.corda.docs.java.tutorial.twoparty;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.docs.java.tutorial.helloworld.IOUContract;
import net.corda.docs.java.tutorial.helloworld.IOUState;

import java.security.PublicKey;
import java.util.List;

// START OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE
public class IOUFlow2 extends FlowLogic<Void> {
    Integer iouValue = (Integer) new Object();
    Party otherParty = (Party) new Object();
    TransactionBuilder txBuilder = (TransactionBuilder) new Object();
    public Void call() throws FlowException {
// END OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE


        // DOCSTART 01
        // We create the transaction components.
        IOUState outputState = new IOUState(iouValue, getOurIdentity(), otherParty);
        String outputContract = IOUContract.class.getName();
        StateAndContract outputContractAndState = new StateAndContract(outputState, outputContract);
        List<PublicKey> requiredSigners = ImmutableList.of(getOurIdentity().getOwningKey(), otherParty.getOwningKey());
        Command cmd = new Command<>(new IOUContract.Create(), requiredSigners);

        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd);

        // Verifying the transaction.
        txBuilder.verify(getServiceHub());

        // Signing the transaction.
        final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        // Creating a session with the other party.
        FlowSession otherpartySession = initiateFlow(otherParty);

        // Obtaining the counterparty's signature.
        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(
                signedTx, ImmutableList.of(otherpartySession), CollectSignaturesFlow.tracker()));

        // Finalising the transaction.
        subFlow(new FinalityFlow(fullySignedTx));

        return null;
        // DOCEND 01


// START OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE
    }
}
// END OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE