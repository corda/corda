package net.corda.docs.java.tutorial.helloworld;

// DOCSTART 01
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class IOUContract implements Contract {
    // Our Create command.
    public static class Create implements CommandData {
    }

    @Override
    public void verify(LedgerTransaction tx) {
        final CommandWithParties<Create> command = requireSingleCommand(tx.getCommands(), Create.class);

        requireThat(check -> {
            // Constraints on the shape of the transaction.
            check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
            check.using("There should be one output state of type IOUState.", tx.getOutputs().size() == 1);

            // IOU-specific constraints.
            final IOUState out = tx.outputsOfType(IOUState.class).get(0);
            final Party lender = out.getLender();
            final Party borrower = out.getBorrower();
            check.using("The IOU's value must be non-negative.", out.getValue() > 0);
            check.using("The lender and the borrower cannot be the same entity.", lender != borrower);

            // Constraints on the signers.
            final List<PublicKey> signers = command.getSigners();
            check.using("There must only be one signer.", signers.size() == 1);
            check.using("The signer must be the lender.", signers.contains(lender.getOwningKey()));

            return null;
        });
    }
}
// DOCEND 01