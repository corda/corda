package net.corda.legacy.contracts;

import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.contracts.TypeOnlyCommandData;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AnotherDummyContractWithSystemEnvVarDefined implements Contract {
    @NotNull
    private final String magicString = "helloworld";
    @NotNull
    public static final String ANOTHER_DUMMY_PROGRAM_ID = "net.corda.legacy.contracts.AnotherDummyContractWithSystemEnvVarDefined";

    @NotNull
    public final String getMagicString() {
        return this.magicString;
    }

    public void verify(@NotNull LedgerTransaction tx) {

        Intrinsics.checkNotNullParameter(tx, "tx");
        String userName = System.getProperty("user.name");
        if (userName == null || !userName.equals("Alice")) {
            throw new IllegalStateException("System property 'user.name' not valid, wanted 'Alice' but got " + userName);
        }
        String birdSize = System.getProperty("bird.size");
        if (birdSize == null || !birdSize.equals("big")) {
            throw new IllegalStateException("System property 'bird.size' not valid, wanted 'big' but got " + birdSize);
        }
    }

    @NotNull
    public final TransactionBuilder generateInitial(@NotNull PartyAndReference owner, int magicNumber, @NotNull Party notary) {
        Intrinsics.checkNotNullParameter(owner, "owner");
        Intrinsics.checkNotNullParameter(notary, "notary");
        State state = new State(magicNumber);
        TransactionBuilder var10000 = new TransactionBuilder(notary);
        Object[] var5 = new Object[]{new StateAndContract((ContractState) state, ANOTHER_DUMMY_PROGRAM_ID), new Command<Commands.Create>(new Commands.Create(), owner.getParty().getOwningKey())};
        return var10000.withItems(var5);
    }

    public final int inspectState(@NotNull ContractState state) {
        Intrinsics.checkNotNullParameter(state, "state");
        return ((State) state).getMagicNumber();
    }

    public interface Commands extends CommandData {
        public static final class Create extends TypeOnlyCommandData implements Commands {
        }
    }

    public static final class State implements ContractState {
        private final int magicNumber;

        public State(int magicNumber) {
            this.magicNumber = magicNumber;
        }

        public final int getMagicNumber() {
            return this.magicNumber;
        }

        @NotNull
        public List<AbstractParty> getParticipants() {
            return CollectionsKt.emptyList();
        }

        public final int component1() {
            return this.magicNumber;
        }

        @NotNull
        public final State copy(int magicNumber) {
            return new State(magicNumber);
        }

        // $FF: synthetic method
        public static State copy$default(State var0, int var1, int var2, Object var3) {
            if ((var2 & 1) != 0) {
                var1 = var0.magicNumber;
            }

            return var0.copy(var1);
        }

        @NotNull
        public String toString() {
            return "State(magicNumber=" + this.magicNumber + ')';
        }

        public int hashCode() {
            return Integer.hashCode(this.magicNumber);
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            } else if (!(other instanceof State)) {
                return false;
            } else {
                State var2 = (State) other;
                return this.magicNumber == var2.magicNumber;
            }
        }
    }
}
