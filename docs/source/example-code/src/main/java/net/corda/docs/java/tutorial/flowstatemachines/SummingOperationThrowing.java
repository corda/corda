package net.corda.docs.java.tutorial.flowstatemachines;

import net.corda.core.concurrent.CordaFuture;
import net.corda.core.internal.FlowAsyncOperation;
import org.jetbrains.annotations.NotNull;

// DOCSTART SummingOperationThrowing
public final class SummingOperationThrowing implements FlowAsyncOperation<Integer> {
    private final int a;
    private final int b;

    @NotNull
    @Override
    public CordaFuture<Integer> execute() {
        throw new IllegalStateException("You shouldn't be calling me");
    }

    public final int getA() {
        return this.a;
    }

    public final int getB() {
        return this.b;
    }

    public SummingOperationThrowing(int a, int b) {
        this.a = a;
        this.b = b;
    }
}
// DOCEND SummingOperationThrowing
