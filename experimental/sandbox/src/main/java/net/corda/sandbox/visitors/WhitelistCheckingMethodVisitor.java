/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox.visitors;

import net.corda.sandbox.CandidacyStatus;
import net.corda.sandbox.CandidateMethod;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.corda.sandbox.Utils;
import org.objectweb.asm.Label;

/**
 * A MethodVisitor which checks method instructions in order to determine if this
 * method is deterministic or not
 */
final class WhitelistCheckingMethodVisitor extends MethodVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhitelistCheckingMethodVisitor.class);

    private final CandidacyStatus candidacyStatus;
    private final String currentMethodName;

    public WhitelistCheckingMethodVisitor(final MethodVisitor methodVisitor, final CandidacyStatus initialCandidacyStatus, String methodName) {
        super(Opcodes.ASM5, methodVisitor);
        candidacyStatus = initialCandidacyStatus;
        currentMethodName = methodName;
    }

    /**
     * Visits a method instruction. A method instruction is an instruction that
     * invokes a method.
     * <p>
     * Some method instructions are by their nature un-deterministic, so we set those methods to have a
     * {@link CandidateMethod.State#DISALLOWED} State
     */
    @Override
    public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc, final boolean itf) {

        final CandidateMethod candidateMethod = candidacyStatus.getCandidateMethod(currentMethodName);
        final String internalName = owner + "." + name + ":" + desc;
        if (candidacyStatus.putIfAbsent(internalName)) {
            candidacyStatus.addToBacklog(internalName);
        }
        final CandidateMethod referencedCandidateMethod = candidacyStatus.getCandidateMethod(internalName);
        candidateMethod.addReferencedCandidateMethod(referencedCandidateMethod);

        final String methodDetails = owner + " name [" + name + "], desc [" + desc + "]";

        switch (opcode) {
            case Opcodes.INVOKEVIRTUAL:
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("Visiting with INVOKEVIRTUAL: " + methodDetails);
                break;
            case Opcodes.INVOKESTATIC:
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("Visiting with INVOKESTATIC: " + methodDetails);
                break;
            case Opcodes.INVOKESPECIAL:
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("Visiting with INVOKESPECIAL: " + methodDetails);
                break;
            case Opcodes.INVOKEINTERFACE:
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("Visiting with INVOKEINTERFACE: " + methodDetails);
                break;
            // NOTE: case Opcodes.INVOKEDYNAMIC is handled by the visitInvokeDynamicInsn call
            default:
                throw new IllegalArgumentException("Got an unexpected opcode: " + opcode + " in " + currentMethodName);
        }
    }

    @Override
    public void visitTryCatchBlock(final Label start, final Label end, final Label handler, final String type) {
        if (type == null)
            throw new IllegalArgumentException("Exception type must not be null in try/catch block in " + currentMethodName);

        // Forcible disallow attempts to catch ThreadDeath or any throwable superclass - preserve determinism
        if (type.equals(Utils.THREAD_DEATH) || type.equals(Utils.ERROR) || type.equals(Utils.THROWABLE)) {
            final CandidateMethod candidateMethod = candidacyStatus.getCandidateMethod(currentMethodName);
            candidateMethod.disallowed("Method " + currentMethodName + " attempts to catch ThreadDeath, Error or Throwable");
        }
    }

    /**
     * Currently a no-op.
     * <p>
     * The JVMspec seems to permit the possibility of using a backwards branch in a
     * tableswitch to try to create an infinite loop. However, it seems to be
     * impossible in practice - the specification of StackMapFrame seems to prevent
     * it in modern classfile formats, and even by explicitly generating a version
     * 49 (Java 5) classfile, the verifier seems to be specifically resistant to a
     * backwards branch from a tableswitch.
     * <p>
     * We could still add a belt-and-braces static instrumentation to protect
     * against this but it currently seems unnecessary - at worse it is a branch that
     * should count against the branch limit, or an explicit disallow of a backwards
     * branch. Of course, if you find a way to exploit this, we'd welcome a pull
     * request.
     *
     * @param min
     * @param max
     * @param dflt
     * @param labels
     */
    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    /**
     * Visits an invokedynamic instruction - which is specifically disallowed for
     * deterministic apps.
     *
     * @param name
     * @param desc
     * @param bsm
     * @param bsmArgs
     */
    @Override
    public void visitInvokeDynamicInsn(final String name, final String desc, final Handle bsm, final Object... bsmArgs) {
        final String methodDetails = "name [" + name + "], desc [" + desc + "]";
        final CandidateMethod candidateMethod = candidacyStatus.getCandidateMethod(currentMethodName);
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Visiting with INVOKEDYNAMIC:" + methodDetails);
        candidateMethod.disallowed("InvokeDynamic in " + currentMethodName + " with " + methodDetails);
    }

    /**
     * If all the call instructions are deterministic for the referenced candidate methods,
     * then so is this one
     */
    @Override
    public void visitEnd() {
        // Start from the assumption that the method is deterministic, and try to disprove
        CandidateMethod.State checkState = CandidateMethod.State.DETERMINISTIC;
        final CandidateMethod candidateMethod = candidacyStatus.getCandidateMethod(currentMethodName);
        if (candidateMethod == null) {
            throw new IllegalArgumentException(currentMethodName + " not found in CandidacyStatus");
        }
        if (candidateMethod.getCurrentState() == CandidateMethod.State.DISALLOWED) {
            return;
        }

        CHECK:
        for (CandidateMethod referredMethod : candidateMethod.getReferencedCandidateMethods()) {
            CandidateMethod.State childMethodState = referredMethod.getCurrentState();
            switch (childMethodState) {
                case DETERMINISTIC:
                    break;
                case MENTIONED:
                    checkState = CandidateMethod.State.MENTIONED;
                    break;
                case DISALLOWED:
                    checkState = CandidateMethod.State.DISALLOWED;
                    break CHECK;
                case SCANNED:
                    checkState = CandidateMethod.State.MENTIONED;
                    if (referredMethod != candidateMethod)
                        throw new IllegalStateException("Illegal state of method " + referredMethod.getInternalMethodName() + " occurred when visiting method " + currentMethodName);
                    break;
                default:
                    throw new IllegalStateException("Illegal state occurred when visiting method " + currentMethodName);
            }
        }
        candidateMethod.setCurrentState(checkState);

        // If this methods state hasn't already been determined, it should be set to SCANNED
        if (candidateMethod.getCurrentState() == CandidateMethod.State.MENTIONED)
            candidateMethod.scanned();
    }
}
