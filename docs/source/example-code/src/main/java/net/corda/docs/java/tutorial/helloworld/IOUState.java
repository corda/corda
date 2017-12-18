package net.corda.docs.java.tutorial.helloworld;

import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import java.util.List;

// DOCSTART 01
// Add these imports:
import com.google.common.collect.ImmutableList;
import net.corda.core.identity.Party;

// Replace TemplateState's definition with:
public class IOUState implements ContractState {
    private final int value;
    private final Party lender;
    private final Party borrower;

    public IOUState(int value, Party lender, Party borrower) {
        this.value = value;
        this.lender = lender;
        this.borrower = borrower;
    }

    public int getValue() {
        return value;
    }

    public Party getLender() {
        return lender;
    }

    public Party getBorrower() {
        return borrower;
    }

    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(lender, borrower);
    }
}
// DOCEND 01