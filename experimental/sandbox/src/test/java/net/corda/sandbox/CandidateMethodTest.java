package net.corda.sandbox;

import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

/**
 * Tests governing the CandidateMethod
 */
public class CandidateMethodTest {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CandidateMethodTest.class);
    private static final String OBJECT_INIT_METHOD = "java/lang/Object.<init>:()V";
    private static final String SYSTEM_OUT_PRINTLN = "java/io/PrintStream.println:(Ljava/lang/String;)V";

    private CandidateMethod candidateMethod;

    @Test
    public void given_NewCandidateMethod_when_GetState_then_StateIsUndetermined() {
        candidateMethod = CandidateMethod.of(OBJECT_INIT_METHOD);
        assertEquals(CandidateMethod.State.MENTIONED, candidateMethod.getCurrentState());
    }

    @Test
    public void given_CandidateMethod_when_proven_then_StateIsDeterministic() {
        candidateMethod = CandidateMethod.proven(OBJECT_INIT_METHOD);
        assertEquals(CandidateMethod.State.DETERMINISTIC, candidateMethod.getCurrentState());
    }

    @Test
    public void given_CandidateMethod_when_disallowed_then_StateIsDisallowed() {
        candidateMethod = CandidateMethod.of(SYSTEM_OUT_PRINTLN);
        candidateMethod.disallowed("dummy");
        assertEquals(CandidateMethod.State.DISALLOWED, candidateMethod.getCurrentState());
    }

}
