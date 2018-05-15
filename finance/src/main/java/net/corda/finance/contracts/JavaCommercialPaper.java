/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts;

import co.paralleluniverse.fibers.Suspendable;
import kotlin.Unit;
import net.corda.core.contracts.*;
import net.corda.core.crypto.NullKeys.NullPublicKey;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.identity.PartyAndCertificate;
import net.corda.core.node.ServiceHub;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.finance.contracts.asset.Cash;
import net.corda.finance.utils.StateSumming;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.Currency;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * This is a Java version of the CommercialPaper contract (chosen because it's simple). This demonstrates how the
 * use of Kotlin for implementation of the framework does not impose the same language choice on contract developers.
 */
@SuppressWarnings("unused")
public class JavaCommercialPaper implements Contract {
    public static final String JCP_PROGRAM_ID = "net.corda.finance.contracts.JavaCommercialPaper";

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
        public CommandAndState withNewOwner(@NotNull AbstractParty newOwner) {
            return new CommandAndState(new Commands.Move(), new State(this.issuance, newOwner, this.faceValue, this.maturityDate));
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

        @Override
        public boolean equals(Object that) {
            if (this == that) return true;
            if (that == null || getClass() != that.getClass()) return false;

            State state = (State) that;

            if (issuance != null ? !issuance.equals(state.issuance) : state.issuance != null) return false;
            if (owner != null ? !owner.equals(state.owner) : state.owner != null) return false;
            if (faceValue != null ? !faceValue.equals(state.faceValue) : state.faceValue != null) return false;
            return maturityDate != null ? maturityDate.equals(state.maturityDate) : state.maturityDate == null;
        }

        @Override
        public int hashCode() {
            int result = issuance != null ? issuance.hashCode() : 0;
            result = 31 * result + (owner != null ? owner.hashCode() : 0);
            result = 31 * result + (faceValue != null ? faceValue.hashCode() : 0);
            result = 31 * result + (maturityDate != null ? maturityDate.hashCode() : 0);
            return result;
        }

        State withoutOwner() {
            return new State(issuance, new AnonymousParty(NullPublicKey.INSTANCE), faceValue, maturityDate);
        }

        @NotNull
        @Override
        public List<AbstractParty> getParticipants() {
            return Collections.singletonList(this.owner);
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
    private List<CommandWithParties<Commands>> extractCommands(@NotNull LedgerTransaction tx) {
        return tx.getCommands()
                .stream()
                .filter((CommandWithParties<CommandData> command) -> command.getValue() instanceof Commands)
                .map((CommandWithParties<CommandData> command) -> new CommandWithParties<>(command.getSigners(), command.getSigningParties(), (Commands) command.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {

        // Group by everything except owner: any modification to the CP at all is considered changing it fundamentally.
        final List<LedgerTransaction.InOutGroup<State, State>> groups = tx.groupStates(State.class, State::withoutOwner);

        // There are two possible things that can be done with this CP. The first is trading it. The second is redeeming
        // it for cash on or after the maturity date.
        final List<CommandWithParties<CommandData>> commands = tx.getCommands().stream().filter(
                it -> it.getValue() instanceof Commands
        ).collect(Collectors.toList());
        final CommandWithParties<CommandData> command = onlyElementOf(commands);
        final TimeWindow timeWindow = tx.getTimeWindow();

        for (final LedgerTransaction.InOutGroup<State, State> group : groups) {
            final List<State> inputs = group.getInputs();
            final List<State> outputs = group.getOutputs();
            if (command.getValue() instanceof Commands.Move) {
                final CommandWithParties<Commands.Move> cmd = requireSingleCommand(tx.getCommands(), Commands.Move.class);
                // There should be only a single input due to aggregation above
                final State input = onlyElementOf(inputs);

                if (!cmd.getSigners().contains(input.getOwner().getOwningKey()))
                    throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

                // Check the output CP state is the same as the input state, ignoring the owner field.
                if (outputs.size() != 1) {
                    throw new IllegalStateException("the state is propagated");
                }
            } else if (command.getValue() instanceof Commands.Redeem) {
                final CommandWithParties<Commands.Redeem> cmd = requireSingleCommand(tx.getCommands(), Commands.Redeem.class);

                // There should be only a single input due to aggregation above
                final State input = onlyElementOf(inputs);

                if (!cmd.getSigners().contains(input.getOwner().getOwningKey()))
                    throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

                final Instant time = timeWindow == null
                        ? null
                        : timeWindow.getUntilTime();
                final Amount<Issued<Currency>> received = StateSumming.sumCashBy(tx.getOutputStates(), input.getOwner());

                requireThat(require -> {
                    require.using("must be timestamped", timeWindow != null);
                    require.using("received amount equals the face value: "
                            + received + " vs " + input.getFaceValue(), received.equals(input.getFaceValue()));
                    require.using("the paper must have matured", time != null && !time.isBefore(input.getMaturityDate()));
                    require.using("the received amount equals the face value", input.getFaceValue().equals(received));
                    require.using("the paper must be destroyed", outputs.isEmpty());
                    return Unit.INSTANCE;
                });
            } else if (command.getValue() instanceof Commands.Issue) {
                final CommandWithParties<Commands.Issue> cmd = requireSingleCommand(tx.getCommands(), Commands.Issue.class);
                final State output = onlyElementOf(outputs);
                final Instant time = null == timeWindow
                        ? null
                        : timeWindow.getUntilTime();

                requireThat(require -> {
                    require.using("output values sum to more than the inputs", inputs.isEmpty());
                    require.using("output values sum to more than the inputs", output.faceValue.getQuantity() > 0);
                    require.using("must be timestamped", timeWindow != null);
                    require.using("the maturity date is not in the past", time != null && time.isBefore(output.getMaturityDate()));
                    require.using("output states are issued by a command signer", cmd.getSigners().contains(output.issuance.getParty().getOwningKey()));
                    return Unit.INSTANCE;
                });
            }
        }
    }

    public TransactionBuilder generateIssue(@NotNull PartyAndReference issuance, @NotNull Amount<Issued<Currency>> faceValue, @Nullable Instant maturityDate, @NotNull Party notary, Integer encumbrance) {
        State state = new State(issuance, issuance.getParty(), faceValue, maturityDate);
        TransactionState output = new TransactionState<>(state, JCP_PROGRAM_ID, notary, encumbrance);
        return new TransactionBuilder(notary).withItems(output, new Command<>(new Commands.Issue(), issuance.getParty().getOwningKey()));
    }

    public TransactionBuilder generateIssue(@NotNull PartyAndReference issuance, @NotNull Amount<Issued<Currency>> faceValue, @Nullable Instant maturityDate, @NotNull Party notary) {
        return generateIssue(issuance, faceValue, maturityDate, notary, null);
    }

    @Suspendable
    public void generateRedeem(final TransactionBuilder tx,
                               final StateAndRef<State> paper,
                               final ServiceHub services,
                               final PartyAndCertificate ourIdentity) throws InsufficientBalanceException {
        Cash.generateSpend(services, tx, Structures.withoutIssuer(paper.getState().getData().getFaceValue()), ourIdentity, paper.getState().getData().getOwner(), Collections.emptySet());
        tx.addInputState(paper);
        tx.addCommand(new Command<>(new Commands.Redeem(), paper.getState().getData().getOwner().getOwningKey()));
    }

    public void generateMove(TransactionBuilder tx, StateAndRef<State> paper, AbstractParty newOwner) {
        tx.addInputState(paper);
        tx.addOutputState(new TransactionState<>(new State(paper.getState().getData().getIssuance(), newOwner, paper.getState().getData().getFaceValue(), paper.getState().getData().getMaturityDate()), JCP_PROGRAM_ID, paper.getState().getNotary(), paper.getState().getEncumbrance()));
        tx.addCommand(new Command<>(new Commands.Move(), paper.getState().getData().getOwner().getOwningKey()));
    }

    private static <T> T onlyElementOf(Iterable<T> iterable) {
        Iterator<T> iter = iterable.iterator();
        T item = iter.next();
        if (iter.hasNext()) {
            throw new IllegalArgumentException("Iterable has more than one element!");
        }
        return item;
    }
}
