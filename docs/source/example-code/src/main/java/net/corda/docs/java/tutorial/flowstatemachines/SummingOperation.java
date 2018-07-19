package net.corda.docs.java.tutorial.flowstatemachines;

import net.corda.core.concurrent.CordaFuture;
import net.corda.core.internal.FlowAsyncOperation;
import net.corda.core.internal.concurrent.CordaFutureImplKt;
import org.jetbrains.annotations.NotNull;

// DOCSTART SummingOperation
public final class SummingOperation implements FlowAsyncOperation<Integer> {
    private final int a;
    private final int b;

    @NotNull
    @Override
    public CordaFuture<Integer> execute() {
        return CordaFutureImplKt.doneFuture(this.a + this.b);
    }

    public final int getA() {
        return this.a;
    }

    public final int getB() {
        return this.b;
    }

    public SummingOperation(int a, int b) {
        this.a = a;
        this.b = b;
    }
}
// DOCEND SummingOperation
