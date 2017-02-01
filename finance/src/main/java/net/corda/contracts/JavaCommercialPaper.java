package net.corda.contracts;

import com.google.common.collect.*;
import kotlin.*;
import net.corda.contracts.asset.*;
import net.corda.core.contracts.*;
import net.corda.core.contracts.TransactionForContract.*;
import net.corda.core.contracts.clauses.*;
import net.corda.core.crypto.*;
import net.corda.core.node.services.*;
import net.corda.core.transactions.*;
import org.jetbrains.annotations.*;

import java.time.*;
import java.util.*;
import java.util.stream.*;

import static kotlin.collections.CollectionsKt.*;
import static net.corda.core.contracts.ContractsDSL.*;


/**
 * This is a Java version of the CommercialPaper contract (chosen because it's simple). This demonstrates how the
 * use of Kotlin for implementation of the framework does not impose the same language choice on contract developers.
 */
public class JavaCommercialPaper implements Contract {
    private static final Contract JCP_PROGRAM_ID = new JavaCommercialPaper();

    public static class State implements OwnableState, ICommercialPaperState {
        private PartyAndReference issuance;
        private CompositeKey owner;
        private Amount<Issued<Currency>> faceValue;
        private Instant maturityDate;

        public State() {
        }  // For serialization

        public State(PartyAndReference issuance, CompositeKey owner, Amount<Issued<Currency>> faceValue,
                     Instant maturityDate) {
            this.issuance = issuance;
            this.owner = owner;
            this.faceValue = faceValue;
            this.maturityDate = maturityDate;
        }

        public State copy() {
            return new State(this.issuance, this.owner, this.faceValue, this.maturityDate);
        }

        public ICommercialPaperState withOwner(CompositeKey newOwner) {
            return new State(this.issuance, newOwner, this.faceValue, this.maturityDate);
        }

        @NotNull
        @Override
        public Pair<CommandData, OwnableState> withNewOwner(@NotNull CompositeKey newOwner) {
            return new Pair<>(new Commands.Move(), new State(this.issuance, newOwner, this.faceValue, this.maturityDate));
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

        @NotNull
        public CompositeKey getOwner() {
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
            return new State(issuance, CryptoUtilities.getNullCompositeKey(), faceValue, maturityDate);
        }

        @NotNull
        @Override
        public List<CompositeKey> getParticipants() {
            return ImmutableList.of(this.owner);
        }
    }

    public interface Clauses {
        class Group extends GroupClauseVerifier<State, Commands, State> {
            // This complains because we're passing generic types into a varargs, but it is valid so we suppress the
            // warning.
            @SuppressWarnings("unchecked")
            Group() {
                super(new AnyOf<>(
                    new Clauses.Redeem(),
                    new Clauses.Move(),
                    new Clauses.Issue()
                ));
            }

            @NotNull
            @Override
            public List<InOutGroup<State, State>> groupStates(@NotNull TransactionForContract tx) {
                return tx.groupStates(State.class, State::withoutOwner);
            }
        }

        class Move extends Clause<State, Commands, State> {
            @NotNull
            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Move.class);
            }

            @NotNull
            @Override
            public Set<Commands> verify(@NotNull TransactionForContract tx,
                                        @NotNull List<? extends State> inputs,
                                        @NotNull List<? extends State> outputs,
                                        @NotNull List<? extends AuthenticatedObject<? extends Commands>> commands,
                                        @NotNull State groupingKey) {
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

        class Redeem extends Clause<State, Commands, State> {
            @NotNull
            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Redeem.class);
            }

            @NotNull
            @Override
            public Set<Commands> verify(@NotNull TransactionForContract tx,
                                        @NotNull List<? extends State> inputs,
                                        @NotNull List<? extends State> outputs,
                                        @NotNull List<? extends AuthenticatedObject<? extends Commands>> commands,
                                        @NotNull State groupingKey) {
                AuthenticatedObject<Commands.Redeem> cmd = requireSingleCommand(tx.getCommands(), Commands.Redeem.class);

                // There should be only a single input due to aggregation above
                State input = single(inputs);

                if (!cmd.getSigners().contains(input.getOwner()))
                    throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

                Timestamp timestamp = tx.getTimestamp();
                Instant time = null == timestamp
                        ? null
                        : timestamp.getBefore();
                Amount<Issued<Currency>> received = CashKt.sumCashBy(tx.getOutputs(), input.getOwner());

                requireThat(require -> {
                    require.by("must be timestamped", timestamp != null);
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

        class Issue extends Clause<State, Commands, State> {
            @NotNull
            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Issue.class);
            }

            @NotNull
            @Override
            public Set<Commands> verify(@NotNull TransactionForContract tx,
                                        @NotNull List<? extends State> inputs,
                                        @NotNull List<? extends State> outputs,
                                        @NotNull List<? extends AuthenticatedObject<? extends Commands>> commands,
                                        @NotNull State groupingKey) {
                AuthenticatedObject<Commands.Issue> cmd = requireSingleCommand(tx.getCommands(), Commands.Issue.class);
                State output = single(outputs);
                Timestamp timestampCommand = tx.getTimestamp();
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
            public boolean equals(Object obj) {
                return obj instanceof Move;
            }
        }

        class Redeem implements Commands {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Redeem;
            }
        }

        class Issue implements Commands {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Issue;
            }
        }
    }

    @NotNull
    private List<AuthenticatedObject<Commands>> extractCommands(@NotNull TransactionForContract tx) {
        return tx.getCommands()
                .stream()
                .filter((AuthenticatedObject<CommandData> command) -> command.getValue() instanceof Commands)
                .map((AuthenticatedObject<CommandData> command) -> new AuthenticatedObject<>(command.getSigners(), command.getSigningParties(), (Commands) command.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public void verify(@NotNull TransactionForContract tx) throws IllegalArgumentException {
        ClauseVerifier.verifyClause(tx, new Clauses.Group(), extractCommands(tx));
    }

    @NotNull
    @Override
    public SecureHash getLegalContractReference() {
        // TODO: Should return hash of the contract's contents, not its URI
        return SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper");
    }

    public TransactionBuilder generateIssue(@NotNull PartyAndReference issuance, @NotNull Amount<Issued<Currency>> faceValue, @Nullable Instant maturityDate, @NotNull Party.Full notary, Integer encumbrance) {
        State state = new State(issuance, issuance.getParty().getOwningKey(), faceValue, maturityDate);
        TransactionState output = new TransactionState<>(state, notary, encumbrance);
        return new TransactionType.General.Builder(notary).withItems(output, new Command(new Commands.Issue(), issuance.getParty().getOwningKey()));
    }

    public TransactionBuilder generateIssue(@NotNull PartyAndReference issuance, @NotNull Amount<Issued<Currency>> faceValue, @Nullable Instant maturityDate, @NotNull Party.Full notary) {
        return generateIssue(issuance, faceValue, maturityDate, notary, null);
    }

    public void generateRedeem(TransactionBuilder tx, StateAndRef<State> paper, VaultService vault) throws InsufficientBalanceException {
        vault.generateSpend(tx, StructuresKt.withoutIssuer(paper.getState().getData().getFaceValue()), paper.getState().getData().getOwner(), null);
        tx.addInputState(paper);
        tx.addCommand(new Command(new Commands.Redeem(), paper.getState().getData().getOwner()));
    }

    public void generateMove(TransactionBuilder tx, StateAndRef<State> paper, CompositeKey newOwner) {
        tx.addInputState(paper);
        tx.addOutputState(new TransactionState<>(new State(paper.getState().getData().getIssuance(), newOwner, paper.getState().getData().getFaceValue(), paper.getState().getData().getMaturityDate()), paper.getState().getNotary(), paper.getState().getEncumbrance()));
        tx.addCommand(new Command(new Commands.Move(), paper.getState().getData().getOwner()));
    }
}
