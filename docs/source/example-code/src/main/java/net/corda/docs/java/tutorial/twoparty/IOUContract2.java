package net.corda.docs.java.tutorial.twoparty;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.Command;
import net.corda.core.identity.Party;

import static net.corda.core.contracts.ContractsDSL.requireThat;

// START OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE
public class IOUContract2 {
    public static void main(String[] args) {
        Command command = (Command) new Object();
        Party borrower = (Party) new Object();
        Party lender = (Party) new Object();
        requireThat(check -> {
// END OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE


            // DOCSTART 01
            // Constraints on the signers.
            check.using("There must be two signers.", command.getSigners().size() == 2);
            check.using("The borrower and lender must be signers.", command.getSigners().containsAll(
                    ImmutableList.of(borrower.getOwningKey(), lender.getOwningKey())));
            // DOCEND 01


// START OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE
            return null;
        });
    }
}
// END OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE