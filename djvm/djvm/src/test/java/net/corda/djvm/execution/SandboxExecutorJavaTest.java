package net.corda.djvm.execution;

import net.corda.djvm.TestBase;
import net.corda.djvm.WithJava;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Function;

import static java.util.Collections.singleton;
import static net.corda.djvm.messages.Severity.WARNING;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class SandboxExecutorJavaTest extends TestBase {
    private static final int TX_ID = 101;

    @Test
    public void testTransaction() {
        //TODO: Transaction should not be a pinned class! It needs
        //      to be marshalled into and out of the sandbox.
        Set<Class<?>> pinnedClasses = singleton(Transaction.class);
        sandbox(new Object[0], pinnedClasses, WARNING, true, ctx -> {
            SandboxExecutor<Transaction, Void> contractExecutor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            Transaction tx = new Transaction(TX_ID);
            assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WithJava.run(contractExecutor, ContractWrapper.class, tx))
                .withMessageContaining("Contract constraint violated: txId=" + TX_ID);
            return null;
        });
    }

    public interface Contract {
        @SuppressWarnings("unused")
        void verify(Transaction tx);
    }

    public static class ContractImplementation implements Contract {
        @Override
        public void verify(Transaction tx) {
            throw new IllegalArgumentException("Contract constraint violated: txId=" + tx.getId());
        }
    }

    public static class ContractWrapper implements Function<Transaction, Void> {
        @Override
        public Void apply(Transaction input) {
            new ContractImplementation().verify(input);
            return null;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class Transaction {
        private final int id;

        Transaction(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }
}