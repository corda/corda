package net.corda.contracts;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import kotlin.Pair;
import kotlin.Unit;
import net.corda.contracts.asset.CashKt;
import net.corda.core.contracts.*;
import net.corda.core.contracts.clauses.AnyOf;
import net.corda.core.contracts.clauses.Clause;
import net.corda.core.contracts.clauses.ClauseVerifier;
import net.corda.core.contracts.clauses.GroupClauseVerifier;
import net.corda.core.crypto.testing.NullPublicKey;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.VaultService;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * This is a Java version of the CommercialPaper contract (chosen because it's simple). This demonstrates how the
 * use of Kotlin for implementation of the framework does not impose the same language choice on contract developers.
 */
@SuppressWarnings("unused")
public class JavaCommercialPaper implements Contract {
    private static final Contract JCP_PROGRAM_ID = new JavaCommercialPaper();

    @SuppressWarnings("unused")
    public static class State implements OwnableState, ICommercialPaperState {
        private PartyAndReference issuance;
        private AbstractParty owner;
        private Amount<Issued<Currency>> faceValue;
        private Instant maturityDate;

        public State() {
        }  // For serialization

        public State(PartyAndReference issuance, AbstractParty owner, Amount<Issued<Currency>> faceValue,
                     Instant maturityDate) {
            this.issuance = issuance;
            this.owner = owner;
            this.faceValue = faceValue;
            this.maturityDate = maturityDate;
        }

        public State copy() {
            return new State(this.issuance, this.owner, this.faceValue, this.maturityDate);
        }

        public ICommercialPaperState withOwner(AbstractParty newOwner) {
            return new State(this.issuance, newOwner, this.faceValue, this.maturityDate);
        }

        @NotNull
        @Override
        public Pair<CommandData, OwnableState> withNewOwner(@NotNull AbstractParty newOwner) {
            return new Pair<>(new Commands.Move(), new State(this.issuance, newOwner, this.faceValue, this.maturityDate));
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
        public AbstractParty getOwner() {
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
        public boolean equals(Object that) {
            if (this == that) return true;
            if (that == null || getClass() != that.getClass()) return false;

            State state = (State) that;

            if (issuance != null ? !issuance.equals(state.issuance) : state.issuance != null) return false;
            if (owner != null ? !owner.equals(state.owner) : state.owner != null) return false;
            if (faceValue != null ? !faceValue.equals(state.faceValue) : state.faceValue != null) return false;
            if (maturityDate != null ? !maturityDate.equals(state.maturityDate) : state.maturityDate != null) return false;
            return true;
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
            return new State(issuance, new AnonymousParty(NullPublicKey.INSTANCE), faceValue, maturityDate);
        }

        @NotNull
        @Override
        public List<AbstractParty> getParticipants() {
            return ImmutableList.of(this.owner);
        }
    }

    public interface Clauses {
        @SuppressWarnings("unused")
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
            public List<LedgerTransaction.InOutGroup<State, State>> groupStates(@NotNull LedgerTransaction tx) {
                return tx.groupStates(State.class, State::withoutOwner);
            }
        }

        @SuppressWarnings("unused")
        class Move extends Clause<State, Commands, State> {
            @NotNull
            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Move.class);
            }

            @NotNull
            @Override
            public Set<Commands> verify(@NotNull LedgerTransaction tx,
                                        @NotNull List<? extends State> inputs,
                                        @NotNull List<? extends State> outputs,
                                        @NotNull List<? extends AuthenticatedObject<? extends Commands>> commands,
                                        State groupingKey) {
                AuthenticatedObject<Commands.Move> cmd = requireSingleCommand(tx.getCommands(), Commands.Move.class);
                // There should be only a single input due to aggregation above
                State input = Iterables.getOnlyElement(inputs);

                if (!cmd.getSigners().contains(input.getOwner().getOwningKey()))
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

        @SuppressWarnings("unused")
        class Redeem extends Clause<State, Commands, State> {
            @NotNull
            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Redeem.class);
            }

            @NotNull
            @Override
            public Set<Commands> verify(@NotNull LedgerTransaction tx,
                                        @NotNull List<? extends State> inputs,
                                        @NotNull List<? extends State> outputs,
                                        @NotNull List<? extends AuthenticatedObject<? extends Commands>> commands,
                                        State groupingKey) {
                AuthenticatedObject<Commands.Redeem> cmd = requireSingleCommand(tx.getCommands(), Commands.Redeem.class);

                // There should be only a single input due to aggregation above
                State input = Iterables.getOnlyElement(inputs);

                if (!cmd.getSigners().contains(input.getOwner().getOwningKey()))
                    throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

                TimeWindow timeWindow = tx.getTimeWindow();
                Instant time = null == timeWindow
                        ? null
                        : timeWindow.getUntilTime();
                Amount<Issued<Currency>> received = CashKt.sumCashBy(tx.getOutputs().stream().map(TransactionState::getData).collect(Collectors.toList()), input.getOwner());

                requireThat(require -> {
                    require.using("must be timestamped", timeWindow != null);
                    require.using("received amount equals the face value: "
                            + received + " vs " + input.getFaceValue(), received.equals(input.getFaceValue()));
                    require.using("the paper must have matured", time != null && !time.isBefore(input.getMaturityDate()));
                    require.using("the received amount equals the face value", input.getFaceValue().equals(received));
                    require.using("the paper must be destroyed", outputs.isEmpty());
                    return Unit.INSTANCE;
                });

                return Collections.singleton(cmd.getValue());
            }
        }

        @SuppressWarnings("unused")
        class Issue extends Clause<State, Commands, State> {
            @NotNull
            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Issue.class);
            }

            @NotNull
            @Override
            public Set<Commands> verify(@NotNull LedgerTransaction tx,
                                        @NotNull List<? extends State> inputs,
                                        @NotNull List<? extends State> outputs,
                                        @NotNull List<? extends AuthenticatedObject<? extends Commands>> commands,
                                        State groupingKey) {
                AuthenticatedObject<Commands.Issue> cmd = requireSingleCommand(tx.getCommands(), Commands.Issue.class);
                State output = Iterables.getOnlyElement(outputs);
                TimeWindow timeWindowCommand = tx.getTimeWindow();
                Instant time = null == timeWindowCommand
                        ? null
                        : timeWindowCommand.getUntilTime();

                requireThat(require -> {
                    require.using("output values sum to more than the inputs", inputs.isEmpty());
                    require.using("output values sum to more than the inputs", output.faceValue.getQuantity() > 0);
                    require.using("must be timestamped", timeWindowCommand != null);
                    require.using("the maturity date is not in the past", time != null && time.isBefore(output.getMaturityDate()));
                    require.using("output states are issued by a command signer", cmd.getSigners().contains(output.issuance.getParty().getOwningKey()));
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
    private List<AuthenticatedObject<Commands>> extractCommands(@NotNull LedgerTransaction tx) {
        return tx.getCommands()
                .stream()
                .filter((AuthenticatedObject<CommandData> command) -> command.getValue() instanceof Commands)
                .map((AuthenticatedObject<CommandData> command) -> new AuthenticatedObject<>(command.getSigners(), command.getSigningParties(), (Commands) command.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        ClauseVerifier.verifyClause(tx, new Clauses.Group(), extractCommands(tx));
    }

    public TransactionBuilder generateIssue(@NotNull PartyAndReference issuance, @NotNull Amount<Issued<Currency>> faceValue, @Nullable Instant maturityDate, @NotNull Party notary, Integer encumbrance) {
        State state = new State(issuance, issuance.getParty(), faceValue, maturityDate);
        TransactionState output = new TransactionState<>(state, notary, encumbrance);
        return new TransactionBuilder(notary).withItems(output, new Command<>(new Commands.Issue(), issuance.getParty().getOwningKey()));
    }

    public TransactionBuilder generateIssue(@NotNull PartyAndReference issuance, @NotNull Amount<Issued<Currency>> faceValue, @Nullable Instant maturityDate, @NotNull Party notary) {
        return generateIssue(issuance, faceValue, maturityDate, notary, null);
    }

    @Suspendable
    public void generateRedeem(TransactionBuilder tx, StateAndRef<State> paper, VaultService vault) throws InsufficientBalanceException {
        vault.generateSpend(tx, Structures.withoutIssuer(paper.getState().getData().getFaceValue()), paper.getState().getData().getOwner(), null);
        tx.addInputState(paper);
        tx.addCommand(new Command<>(new Commands.Redeem(), paper.getState().getData().getOwner().getOwningKey()));
    }

    public void generateMove(TransactionBuilder tx, StateAndRef<State> paper, AbstractParty newOwner) {
        tx.addInputState(paper);
        tx.addOutputState(new TransactionState<>(new State(paper.getState().getData().getIssuance(), newOwner, paper.getState().getData().getFaceValue(), paper.getState().getData().getMaturityDate()), paper.getState().getNotary(), paper.getState().getEncumbrance()));
        tx.addCommand(new Command<>(new Commands.Move(), paper.getState().getData().getOwner().getOwningKey()));
    }
}
