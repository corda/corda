package net.corda.docs.java.tutorial.flowstatemachines;

import net.corda.core.flows.SignTransactionFlow;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.Nullable;

public class TutorialFlowStateMachines {
    // DOCSTART 1
    private final ProgressTracker progressTracker = new ProgressTracker(
            RECEIVING,
            VERIFYING,
            SIGNING,
            COLLECTING_SIGNATURES,
            RECORDING
    );

    private static final ProgressTracker.Step RECEIVING = new ProgressTracker.Step(
            "Waiting for seller trading info");
    private static final ProgressTracker.Step VERIFYING = new ProgressTracker.Step(
            "Verifying seller assets");
    private static final ProgressTracker.Step SIGNING = new ProgressTracker.Step(
            "Generating and signing transaction proposal");
    private static final ProgressTracker.Step COLLECTING_SIGNATURES = new ProgressTracker.Step(
            "Collecting signatures from other parties");
    private static final ProgressTracker.Step RECORDING = new ProgressTracker.Step(
            "Recording completed transaction");
    // DOCEND 1

    // DOCSTART 2
    private static final ProgressTracker.Step VERIFYING_AND_SIGNING = new ProgressTracker.Step("Verifying and signing transaction proposal") {
        @Nullable
        @Override
        public ProgressTracker childProgressTracker() {
            return SignTransactionFlow.Companion.tracker();
        }
    };
    // DOCEND 2
}
