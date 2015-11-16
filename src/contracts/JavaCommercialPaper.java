package contracts;

import core.*;
import core.serialization.*;
import org.jetbrains.annotations.*;

import java.security.*;
import java.time.*;

import static core.ContractsDSLKt.*;
import static kotlin.CollectionsKt.*;

/**
 * This is a Java version of the CommercialPaper contract (chosen because it's simple). This demonstrates how the
 * use of Kotlin for implementation of the framework does not impose the same language choice on contract developers.
 *
 * NOTE: For illustration only. Not unit tested.
 */
public class JavaCommercialPaper implements Contract {
    public static class State implements SerializeableWithKryo {
        private InstitutionReference issuance;
        private PublicKey owner;
        private Amount faceValue;
        private Instant maturityDate;

        public State() {}  // For serialization

        public State(InstitutionReference issuance, PublicKey owner, Amount faceValue, Instant maturityDate) {
            this.issuance = issuance;
            this.owner = owner;
            this.faceValue = faceValue;
            this.maturityDate = maturityDate;
        }

        public InstitutionReference getIssuance() {
            return issuance;
        }

        public PublicKey getOwner() {
            return owner;
        }

        public Amount getFaceValue() {
            return faceValue;
        }

        public Instant getMaturityDate() {
            return maturityDate;
        }
    }

    public static class Commands implements core.Command {
        public static class Move {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Move;
            }
        }

        public static class Redeem {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Redeem;
            }
        }
    }

    @Override
    public void verify(@NotNull TransactionForVerification tx) {
        // There are two possible things that can be done with CP. The first is trading it. The second is redeeming it
        // for cash on or after the maturity date.

        // Find the command that instructs us what to do and check there's exactly one.
        AuthenticatedObject<Command> cmd = requireSingleCommand(tx.getArgs(), Commands.class);
        // For now do not allow multiple pieces of CP to trade in a single transaction. Study this more!
        State input = single(filterIsInstance(tx.getInStates(), State.class));

        if (!cmd.getSigners().contains(input.getOwner()))
            throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

        if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Move) {
            // Check the output CP state is the same as the input state, ignoring the owner field.
            State output = single(filterIsInstance(tx.getOutStates(), State.class));
            if (!output.getFaceValue().equals(input.getFaceValue()) ||
                    !output.getIssuance().equals(input.getIssuance()) ||
                    !output.getMaturityDate().equals(input.getMaturityDate()))
                throw new IllegalStateException("Failed requirement: the output state is the same as the input state except for owner");
        } else if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Redeem) {
            Amount received = CashKt.sumCashOrNull(tx.getInStates());
            if (received == null)
                throw new IllegalStateException("Failed requirement: no cash being redeemed");
            if (input.getMaturityDate().isAfter(tx.getTime()))
                throw new IllegalStateException("Failed requirement: the paper must have matured");
            if (!input.getFaceValue().equals(received))
                throw new IllegalStateException("Failed requirement: the received amount equals the face value");
            if (!filterIsInstance(tx.getOutStates(), State.class).isEmpty())
                throw new IllegalStateException("Failed requirement: the paper must be destroyed");
        }
    }

    @NotNull
    @Override
    public String getLegalContractReference() {
        return "https://en.wikipedia.org/wiki/Commercial_paper";
    }
}
