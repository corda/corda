package net.corda.finance.flows;

import net.corda.core.flows.AbstractStateReplacementFlow;
import net.corda.core.flows.FlowSession;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class AbstractStateReplacementFlowTest {

    // Acceptor used to have a type parameter of Unit which prevented Java code from subclassing it (https://youtrack.jetbrains.com/issue/KT-15964).
    private static class TestAcceptorCanBeInheritedInJava extends AbstractStateReplacementFlow.Acceptor {
        public TestAcceptorCanBeInheritedInJava(@NotNull FlowSession otherSideSession, @NotNull ProgressTracker progressTracker) {
            super(otherSideSession, progressTracker);
        }

        @Override
        protected void verifyProposal(@NotNull SignedTransaction stx, @NotNull AbstractStateReplacementFlow.Proposal proposal) {
        }
    }
}
