package net.corda.docs.java.tutorial.contract;

import net.corda.core.contracts.*;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.transactions.LedgerTransaction.InOutGroup;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;
import static net.corda.finance.utils.StateSumming.sumCashBy;

public class CommercialPaper implements Contract {
    // DOCSTART 1
    public static final String IOU_CONTRACT_ID = "com.example.contract.IOUContract";
    // DOCEND 1

    // DOCSTART 3
    @Override
    public void verify(LedgerTransaction tx) {
        List<InOutGroup<State, State>> groups = tx.groupStates(State.class, State::withoutOwner);
        CommandWithParties<Commands> cmd = requireSingleCommand(tx.getCommands(), Commands.class);
        // DOCEND 3

        // DOCSTART 4
        TimeWindow timeWindow = tx.getTimeWindow();

        for (InOutGroup group : groups) {
            List<State> inputs = group.getInputs();
            List<State> outputs = group.getOutputs();

            if (cmd.getValue() instanceof Commands.Move) {
                State input = inputs.get(0);
                requireThat(require -> {
                    require.using("the transaction is signed by the owner of the CP", cmd.getSigners().contains(input.getOwner().getOwningKey()));
                    require.using("the state is propagated", outputs.size() == 1);
                    // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                    // the input ignoring the owner field due to the grouping.
                    return null;
                });

            } else if (cmd.getValue() instanceof Commands.Redeem) {
                // Redemption of the paper requires movement of on-ledger cash.
                State input = inputs.get(0);
                Amount<Issued<Currency>> received = sumCashBy(tx.getOutputStates(), input.getOwner());
                if (timeWindow == null) throw new IllegalArgumentException("Redemptions must be timestamped");
                Instant time = timeWindow.getFromTime();
                requireThat(require -> {
                    require.using("the paper must have matured", time.isAfter(input.getMaturityDate()));
                    require.using("the received amount equals the face value", received == input.getFaceValue());
                    require.using("the paper must be destroyed", outputs.isEmpty());
                    require.using("the transaction is signed by the owner of the CP", cmd.getSigners().contains(input.getOwner().getOwningKey()));
                    return null;
                });
            } else if (cmd.getValue() instanceof Commands.Issue) {
                State output = outputs.get(0);
                if (timeWindow == null) throw new IllegalArgumentException("Issuances must be timestamped");
                Instant time = timeWindow.getUntilTime();
                requireThat(require -> {
                    // Don't allow people to issue commercial paper under other entities identities.
                    require.using("output states are issued by a command signer", cmd.getSigners().contains(output.getIssuance().getParty().getOwningKey()));
                    require.using("output values sum to more than the inputs", output.getFaceValue().getQuantity() > 0);
                    require.using("the maturity date is not in the past", time.isBefore(output.getMaturityDate()));
                    // Don't allow an existing CP state to be replaced by this issuance.
                    require.using("can't reissue an existing state", inputs.isEmpty());
                    return null;
                });
            } else {
                throw new IllegalArgumentException("Unrecognised command");
            }
        }
        // DOCEND 4
    }

    // DOCSTART 2
    public static class Commands implements CommandData {
        public static class Move extends Commands {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Move;
            }
        }

        public static class Redeem extends Commands {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Redeem;
            }
        }

        public static class Issue extends Commands {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Issue;
            }
        }
    }
    // DOCEND 2
}