package com.r3corda.contracts;

import com.google.common.collect.*;
import com.r3corda.contracts.asset.*;
import com.r3corda.core.contracts.*;
import com.r3corda.core.contracts.TransactionForContract.*;
import com.r3corda.core.crypto.*;
import org.jetbrains.annotations.*;

import java.security.*;
import java.time.*;
import java.util.*;

import static com.r3corda.core.contracts.ContractsDSL.*;
import static kotlin.collections.CollectionsKt.*;


/**
 * This is a Java version of the CommercialPaper contract (chosen because it's simple). This demonstrates how the
 * use of Kotlin for implementation of the framework does not impose the same language choice on contract developers.
 */
public class JavaCommercialPaper implements Contract {
    //public static SecureHash JCP_PROGRAM_ID = SecureHash.sha256("java commercial paper (this should be a bytecode hash)");
    public static Contract JCP_PROGRAM_ID = new JavaCommercialPaper();

    public static class State implements ContractState, ICommercialPaperState {
        private PartyAndReference issuance;
        private PublicKey owner;
        private Amount<Issued<Currency>> faceValue;
        private Instant maturityDate;

        public State() {
        }  // For serialization

        public State(PartyAndReference issuance, PublicKey owner, Amount<Issued<Currency>> faceValue,
                     Instant maturityDate) {
            this.issuance = issuance;
            this.owner = owner;
            this.faceValue = faceValue;
            this.maturityDate = maturityDate;
        }

        public State copy() {
            return new State(this.issuance, this.owner, this.faceValue, this.maturityDate);
        }

        public ICommercialPaperState withOwner(PublicKey newOwner) {
            return new State(this.issuance, newOwner, this.faceValue, this.maturityDate);
        }

        public ICommercialPaperState withIssuance(PartyAndReference newIssuance) {
            return new State(newIssuance, this.owner, this.faceValue, this.maturityDate);
        }

        public ICommercialPaperState withFaceValue(Amount<Issued<Currency>> newFaceValue) {
            return new State(this.issuance, this.owner, newFaceValue, this.maturityDate);
        }

        public ICommercialPaperState withMaturityDate(Instant newMaturityDate) {
            return new State(this.issuance, this.owner, this.faceValue, newMaturityDate);
        }

        public PartyAndReference getIssuance() {
            return issuance;
        }

        public PublicKey getOwner() {
            return owner;
        }

        public Amount<Issued<Currency>> getFaceValue() {
            return faceValue;
        }

        public Instant getMaturityDate() {
            return maturityDate;
        }

        @NotNull
        @Override
        public Contract getContract() {
            return JCP_PROGRAM_ID;
            //return SecureHash.sha256("java commercial paper (this should be a bytecode hash)");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            State state = (State) o;

            if (issuance != null ? !issuance.equals(state.issuance) : state.issuance != null) return false;
            if (owner != null ? !owner.equals(state.owner) : state.owner != null) return false;
            if (faceValue != null ? !faceValue.equals(state.faceValue) : state.faceValue != null) return false;
            return !(maturityDate != null ? !maturityDate.equals(state.maturityDate) : state.maturityDate != null);
        }

        @Override
        public int hashCode() {
            int result = issuance != null ? issuance.hashCode() : 0;
            result = 31 * result + (owner != null ? owner.hashCode() : 0);
            result = 31 * result + (faceValue != null ? faceValue.hashCode() : 0);
            result = 31 * result + (maturityDate != null ? maturityDate.hashCode() : 0);
            return result;
        }

        public State withoutOwner() {
            return new State(issuance, NullPublicKey.INSTANCE, faceValue, maturityDate);
        }

        @NotNull
        @Override
        public List<PublicKey> getParticipants() {
            return ImmutableList.of(this.owner);
        }
    }

    public static class Commands implements CommandData {
        public static class Move extends Commands {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Move;
            }
        }

        public static class Redeem extends Commands {
            private final Party notary;

            public  Redeem(Party setNotary) {
                this.notary = setNotary;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof Redeem;
            }
        }

        public static class Issue extends Commands {
            private final Party notary;

            public  Issue(Party setNotary) {
                this.notary = setNotary;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof Issue;
            }
        }
    }

    @Override
    public void verify(@NotNull TransactionForContract tx) {
        // There are three possible things that can be done with CP.
        // Issuance, trading (aka moving in this prototype) and redeeming.
        // Each command has it's own set of restrictions which the verify function ... verifies.

        List<InOutGroup<State, State>> groups = tx.groupStates(State.class, State::withoutOwner);

        // Find the command that instructs us what to do and check there's exactly one.

        AuthenticatedObject<CommandData> cmd = requireSingleCommand(tx.getCommands(), JavaCommercialPaper.Commands.class);

        for (InOutGroup<State, State> group : groups) {
            List<State> inputs = group.getInputs();
            List<State> outputs = group.getOutputs();

            // For now do not allow multiple pieces of CP to trade in a single transaction.
            if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Issue) {
                Commands.Issue issueCommand = (Commands.Issue) cmd.getValue();
                State output = single(outputs);
                if (!inputs.isEmpty()) {
                    throw new IllegalStateException("Failed Requirement: output values sum to more than the inputs");
                }
                if (output.faceValue.getQuantity() == 0) {
                    throw new IllegalStateException("Failed Requirement: output values sum to more than the inputs");
                }

                TimestampCommand timestampCommand = tx.getTimestampBy(issueCommand.notary);
                if (timestampCommand == null)
                    throw new IllegalArgumentException("Failed Requirement: must be timestamped");

                Instant time = timestampCommand.getBefore();

                if (time == null || !time.isBefore(output.maturityDate)) {
                    throw new IllegalStateException("Failed Requirement: the maturity date is not in the past");
                }

                if (!cmd.getSigners().contains(output.issuance.getParty().getOwningKey())) {
                    throw new IllegalStateException("Failed Requirement: output states are issued by a command signer");
                }
            } else {
                // Everything else (Move, Redeem) requires inputs (they are not first to be actioned)
                // There should be only a single input due to aggregation above
                State input = single(inputs);

                if (!cmd.getSigners().contains(input.getOwner()))
                    throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

                if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Move) {
                    // Check the output CP state is the same as the input state, ignoring the owner field.
                    State output = single(outputs);

                    if (!output.getFaceValue().equals(input.getFaceValue()) ||
                            !output.getIssuance().equals(input.getIssuance()) ||
                            !output.getMaturityDate().equals(input.getMaturityDate()))
                        throw new IllegalStateException("Failed requirement: the output state is the same as the input state except for owner");
                } else if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Redeem) {
                    TimestampCommand timestampCommand = tx.getTimestampBy(((Commands.Redeem) cmd.getValue()).notary);
                    if (timestampCommand == null)
                        throw new IllegalArgumentException("Failed Requirement: must be timestamped");
                    Instant time = timestampCommand.getBefore();

                    Amount<Issued<Currency>> received = CashKt.sumCashBy(tx.getOutputs(), input.getOwner());

                    if (!received.equals(input.getFaceValue()))
                        throw new IllegalStateException("Failed Requirement: received amount equals the face value: "
                            + received + " vs " + input.getFaceValue());
                    if (time == null || time.isBefore(input.getMaturityDate()))
                        throw new IllegalStateException("Failed requirement: the paper must have matured");
                    if (!input.getFaceValue().equals(received))
                        throw new IllegalStateException("Failed requirement: the received amount equals the face value");
                    if (!outputs.isEmpty())
                        throw new IllegalStateException("Failed requirement: the paper must be destroyed");
                }
            }
        }
    }

    @NotNull
    @Override
    public SecureHash getLegalContractReference() {
        // TODO: Should return hash of the contract's contents, not its URI
        return SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper");
    }

    public TransactionBuilder generateIssue(@NotNull PartyAndReference issuance, @NotNull Amount<Issued<Currency>> faceValue, @Nullable Instant maturityDate, @NotNull Party notary) {
        State state = new State(issuance, issuance.getParty().getOwningKey(), faceValue, maturityDate);
        TransactionState output = new TransactionState<>(state, notary);
        return new TransactionType.General.Builder().withItems(output, new Command(new Commands.Issue(notary), issuance.getParty().getOwningKey()));
    }

    public void generateRedeem(TransactionBuilder tx, StateAndRef<State> paper, List<StateAndRef<Cash.State>> wallet) throws InsufficientBalanceException {
        new Cash().generateSpend(tx, paper.getState().getData().getFaceValue(), paper.getState().getData().getOwner(), wallet);
        tx.addInputState(paper);
        tx.addCommand(new Command(new Commands.Redeem(paper.getState().getNotary()), paper.getState().getData().getOwner()));
    }

    public void generateMove(TransactionBuilder tx, StateAndRef<State> paper, PublicKey newOwner) {
        tx.addInputState(paper);
        tx.addOutputState(new TransactionState<>(new State(paper.getState().getData().getIssuance(), newOwner, paper.getState().getData().getFaceValue(), paper.getState().getData().getMaturityDate()), paper.getState().getNotary()));
        tx.addCommand(new Command(new Commands.Move(), paper.getState().getData().getOwner()));
    }
}
