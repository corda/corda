/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox;

import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

/**
 * Tests governing the CandidateMethod
 */
public class CandidateMethodTest {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CandidateMethodTest.class);
    private final static String OBJECT_INIT_METHOD = "java/lang/Object.<init>:()V";
    private final static String SYSTEM_OUT_PRINTLN = "java/io/PrintStream.println:(Ljava/lang/String;)V";

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
