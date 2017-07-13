package net.corda.flows;

import net.corda.core.flows.AbstractStateReplacementFlow;
import net.corda.core.identity.Party;
import net.corda.core.utilities.*;
import org.jetbrains.annotations.*;

@SuppressWarnings("unused")
public class AbstractStateReplacementFlowTest {

    // Acceptor used to have a type parameter of Unit which prevented Java code from subclassing it (https://youtrack.jetbrains.com/issue/KT-15964).
    private static class TestAcceptorCanBeInheritedInJava extends AbstractStateReplacementFlow.Acceptor {
        public TestAcceptorCanBeInheritedInJava(@NotNull Party otherSide, @NotNull ProgressTracker progressTracker) {
            super(otherSide, progressTracker);
        }

        @Override
        protected void verifyProposal(@NotNull AbstractStateReplacementFlow.Proposal proposal) {
        }
    }
}
