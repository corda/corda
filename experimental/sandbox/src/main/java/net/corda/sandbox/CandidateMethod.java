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

import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.Set;

/**
 * A candidate method that is under evaluation. Candidate methods have one of the following states:
 * <p>
 * <ul>
 * <li>{@link CandidateMethod.State#DETERMINISTIC} - It's deterministic and therefore is allowed to be loaded.</li>
 * <li>{@link CandidateMethod.State#DISALLOWED} - It's not deterministic and won't be allowed to be loaded.</li>
 * <li>{@link CandidateMethod.State#SCANNED} - We're not sure if it's deterministic or not.</li>
 * </ul>
 * <p>
 * CandidateMethods themselves reference other CandidateMethods which are be checked for their deterministic state
 */
public final class CandidateMethod {

    // The state model must reflect the difference between "mentioned in an API" and
    // "scanned by classloader"
    public enum State {
        DETERMINISTIC,
        MENTIONED,
        SCANNED,
        DISALLOWED
    }

    private State currentState = State.MENTIONED;

    private String reason;

    private CandidateMethod(String methodSignature) {
        internalMethodName = methodSignature;
    }

    // TODO We'll likely use the formal MethodType for deeper analysis
    private MethodType methodType;

    // Internal method name as it appears in the constant pool
    private final String internalMethodName;

    private final Set<CandidateMethod> referencedCandidateMethods = new HashSet<>();


    public State getCurrentState() {
        return currentState;
    }

    public void setCurrentState(final State currentState) {
        this.currentState = currentState;
    }

    public void disallowed(final String because) {
        reason = because;
        currentState = State.DISALLOWED;
    }

    public void deterministic() {
        if (currentState == State.DISALLOWED) {
            throw new IllegalArgumentException("Method " + internalMethodName + " attempted to transition from DISALLOWED to DETERMINISTIC");
        }
        currentState = State.DETERMINISTIC;
    }

    public void scanned() {
        currentState = State.SCANNED;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getInternalMethodName() {
        return internalMethodName;
    }

    public void addReferencedCandidateMethod(final CandidateMethod referenceCandidateMethod) {
        referencedCandidateMethods.add(referenceCandidateMethod);
    }

    public Set<CandidateMethod> getReferencedCandidateMethods() {
        return referencedCandidateMethods;
    }

    public static CandidateMethod of(String methodSignature) {
        return new CandidateMethod(methodSignature);
    }

    /**
     * This factory constructor is only called for methods that are known to be deterministic in advance
     *
     * @param methodSignature
     * @return
     */
    public static CandidateMethod proven(String methodSignature) {
        final CandidateMethod provenCandidateMethod = new CandidateMethod(methodSignature);
        provenCandidateMethod.deterministic();
        return provenCandidateMethod;
    }

    @Override
    public String toString() {
        return "CandidateMethod{" + "currentState=" + currentState + ", reason=" + reason + ", methodType=" + methodType + ", internalMethodName=" + internalMethodName + ", referencedCandidateMethods=" + referencedCandidateMethods + '}';
    }
}
