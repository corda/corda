package com.r3corda.contracts;

import com.google.common.collect.*;
import com.r3corda.contracts.asset.*;
import com.r3corda.core.contracts.*;
import com.r3corda.core.contracts.TransactionForContract.*;
import com.r3corda.core.contracts.clauses.*;
import com.r3corda.core.crypto.*;
import kotlin.Unit;
import org.jetbrains.annotations.*;

import java.security.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.r3corda.core.contracts.ContractsDSL.*;
import static kotlin.collections.CollectionsKt.*;


/**
 * This is a Java version of the CommercialPaper contract (chosen because it's simple). This demonstrates how the
 * use of Kotlin for implementation of the framework does not impose the same language choice on contract developers.
 */
public class JavaCommercialPaper extends ClauseVerifier {
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

    public interface Clause {
        abstract class AbstractGroup implements GroupClause<State, State> {
            @NotNull
            @Override
            public MatchBehaviour getIfNotMatched() {
                return MatchBehaviour.CONTINUE;
            }

            @NotNull
            @Override
            public MatchBehaviour getIfMatched() {
                return MatchBehaviour.END;
            }
        }

        class Group extends GroupClauseVerifier<State, State> {
            @NotNull
            @Override
            public MatchBehaviour getIfMatched() {
                return MatchBehaviour.END;
            }

            @NotNull
            @Override
            public MatchBehaviour getIfNotMatched() {
                return MatchBehaviour.ERROR;
            }

            @NotNull
            @Override
            public List<GroupClause<State, State>> getClauses() {
                final List<GroupClause<State, State>> clauses = new ArrayList<>();

                clauses.add(new Clause.Redeem());
                clauses.add(new Clause.Move());
                clauses.add(new Clause.Issue());

                return clauses;
            }

            @NotNull
            @Override
            public List<InOutGroup<State, State>> extractGroups(@NotNull TransactionForContract tx) {
                return tx.groupStates(State.class, State::withoutOwner);
            }
        }

        class Move extends AbstractGroup {
            @NotNull
            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Move.class);
            }

            @NotNull
            @Override
            public Set<CommandData> verify(@NotNull TransactionForContract tx,
                                           @NotNull List<? extends State> inputs,
                                           @NotNull List<? extends State> outputs,
                                           @NotNull Collection<? extends AuthenticatedObject<? extends CommandData>> commands,
                                           @NotNull State token) {
                AuthenticatedObject<Commands.Move> cmd = requireSingleCommand(tx.getCommands(), Commands.Move.class);
                // There should be only a single input due to aggregation above
                State input = single(inputs);

                if (!cmd.getSigners().contains(input.getOwner()))
                    throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

                // Check the output CP state is the same as the input state, ignoring the owner field.
                if (outputs.size() != 1) {
                    throw new IllegalStateException("the state is propagated");
                }
                // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                // the input ignoring the owner field due to the grouping.
                return Collections.singleton(cmd.getValue());
            }
        }

        class Redeem extends AbstractGroup {
            @NotNull
            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Redeem.class);
            }

            @NotNull
            @Override
            public Set<CommandData> verify(@NotNull TransactionForContract tx,
                                           @NotNull List<? extends State> inputs,
                                           @NotNull List<? extends State> outputs,
                                           @NotNull Collection<? extends AuthenticatedObject<? extends CommandData>> commands,
                                           @NotNull State token) {
                AuthenticatedObject<Commands.Redeem> cmd = requireSingleCommand(tx.getCommands(), Commands.Redeem.class);

                // There should be only a single input due to aggregation above
                State input = single(inputs);

                if (!cmd.getSigners().contains(input.getOwner()))
                    throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

                Party notary = cmd.getValue().notary;
                TimestampCommand timestampCommand = tx.getTimestampBy(notary);
                Instant time = null == timestampCommand
                        ? null
                        : timestampCommand.getBefore();
                Amount<Issued<Currency>> received = CashKt.sumCashBy(tx.getOutputs(), input.getOwner());

                requireThat(require -> {
                    require.by("must be timestamped", timestampCommand != null);
                    require.by("received amount equals the face value: "
                            + received + " vs " + input.getFaceValue(), received.equals(input.getFaceValue()));
                    require.by("the paper must have matured", time != null && !time.isBefore(input.getMaturityDate()));
                    require.by("the received amount equals the face value", input.getFaceValue().equals(received));
                    require.by("the paper must be destroyed", outputs.isEmpty());
                    return Unit.INSTANCE;
                });

                return Collections.singleton(cmd.getValue());
            }
        }

        class Issue extends AbstractGroup {
            @NotNull
            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Issue.class);
            }

            @NotNull
            @Override
            public Set<CommandData> verify(@NotNull TransactionForContract tx,
                                           @NotNull List<? extends State> inputs,
                                           @NotNull List<? extends State> outputs,
                                           @NotNull Collection<? extends AuthenticatedObject<? extends CommandData>> commands,
                                           @NotNull State token) {
                AuthenticatedObject<Commands.Issue> cmd = requireSingleCommand(tx.getCommands(), Commands.Issue.class);
                State output = single(outputs);
                Party notary = cmd.getValue().notary;
                TimestampCommand timestampCommand = tx.getTimestampBy(notary);
                Instant time = null == timestampCommand
                        ? null
                        : timestampCommand.getBefore();

                requireThat(require -> {
                    require.by("output values sum to more than the inputs", inputs.isEmpty());
                    require.by("output values sum to more than the inputs", output.faceValue.getQuantity() > 0);
                    require.by("must be timestamped", timestampCommand != null);
                    require.by("the maturity date is not in the past", time != null && time.isBefore(output.getMaturityDate()));
                    require.by("output states are issued by a command signer", cmd.getSigners().contains(output.issuance.getParty().getOwningKey()));
                    return Unit.INSTANCE;
                });

                return Collections.singleton(cmd.getValue());
            }
        }
    }

    public interface Commands extends CommandData {
        class Move implements Commands {
            @Override
            public boolean equals(Object obj) { return obj instanceof Move; }
        }

        class Redeem implements Commands {
            private final Party notary;

            public  Redeem(Party setNotary) {
                this.notary = setNotary;
            }

            @Override
            public boolean equals(Object obj) { return obj instanceof Redeem; }
        }

        class Issue implements Commands {
            private final Party notary;

            public  Issue(Party setNotary) {
                this.notary = setNotary;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Issue) {
                    Issue other = (Issue)obj;
                    return notary.equals(other.notary);
                } else {
                    return false;
                }
            }

            @Override
            public int hashCode() { return notary.hashCode(); }
        }
    }

    @NotNull
    @Override
    public List<SingleClause> getClauses() {
        return Collections.singletonList(new Clause.Group());
    }

    @NotNull
    @Override
    public Collection<AuthenticatedObject<CommandData>> extractCommands(@NotNull TransactionForContract tx) {
        return tx.getCommands()
                .stream()
                .filter((AuthenticatedObject<CommandData> command) -> { return command.getValue() instanceof Commands; })
                .collect(Collectors.toList());
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
