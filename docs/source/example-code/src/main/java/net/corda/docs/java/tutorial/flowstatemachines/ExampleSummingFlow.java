package net.corda.docs.java.tutorial.flowstatemachines;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.internal.FlowAsyncOperationKt;
import org.jetbrains.annotations.NotNull;

// DOCSTART ExampleSummingFlow
@StartableByRPC
public final class ExampleSummingFlow extends FlowLogic<Integer> {
    @Suspendable
    @NotNull
    @Override
    public Integer call() {
        return FlowAsyncOperationKt.executeAsync(this, new SummingOperation(1, 2), false);
    }
}
// DOCEND ExampleSummingFlow
