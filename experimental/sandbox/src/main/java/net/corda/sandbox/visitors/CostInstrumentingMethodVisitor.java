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

import net.corda.sandbox.Utils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ben
 */
public final class CostInstrumentingMethodVisitor extends GeneratorAdapter {

    public static final int OP_BREAKPOINT = 0b1100_1010;

    private static final Logger LOGGER = LoggerFactory.getLogger(CostInstrumentingMethodVisitor.class);

    // In future, we may want to have multiple different accounting types
    // for e.g. benchmarking and to determine this at classloading time
    // might be helpful. We may want additional flexibility, e.g. to determine
    // the different accounting instrumenations separately, but this is a good
    // stub
    private final String runtimeAccounterTypeName;

    public CostInstrumentingMethodVisitor(MethodVisitor methodVisitor, int access, String name, String desc) {
        super(Opcodes.ASM5, methodVisitor, access, name, desc);

        runtimeAccounterTypeName = "net/corda/sandbox/costing/RuntimeCostAccounter";
        // save other calling parameters as well...?

    }

    /**
     * This method replaces MONITORENTER / MONITOREXIT opcodes with POP - basically
     * stripping the synchronization out of any sandboxed code.
     *
     * @param opcode
     */
    @Override
    public void visitInsn(final int opcode) {
        switch (opcode) {
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
                super.visitInsn(Opcodes.POP);
                return;
            case Opcodes.ATHROW:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeAccounterTypeName, "recordThrow", "()V", false);
                break;
            case OP_BREAKPOINT:
                throw new IllegalStateException("Illegal opcode BREAKPOINT seen");
        }

        super.visitInsn(opcode);
    }

    /**
     * This method is called when visiting an opcode with a single int operand.
     * For our purposes this is a NEWARRAY opcode.
     *
     * @param opcode
     * @param operand
     */
    @Override
    public void visitIntInsn(final int opcode, final int operand) {
        if (opcode != Opcodes.NEWARRAY) {
            super.visitIntInsn(opcode, operand);
            return;
        }

        // Opcode is NEWARRAY - recordArrayAllocation:(Ljava/lang/String;I)V
        // operand value should be one of Opcodes.T_BOOLEAN, 
        // Opcodes.T_CHAR, Opcodes.T_FLOAT, Opcodes.T_DOUBLE, Opcodes.T_BYTE, 
        // Opcodes.T_SHORT, Opcodes.T_INT or Opcodes.T_LONG.
        final int typeSize;
        switch (operand) {
            case Opcodes.T_BOOLEAN:
            case Opcodes.T_BYTE:
                typeSize = 1;
                break;
            case Opcodes.T_SHORT:
            case Opcodes.T_CHAR:
                typeSize = 2;
                break;
            case Opcodes.T_INT:
            case Opcodes.T_FLOAT:
                typeSize = 4;
                break;
            case Opcodes.T_LONG:
            case Opcodes.T_DOUBLE:
                typeSize = 8;
                break;
            default:
                throw new IllegalStateException("Illegal operand to NEWARRAY seen: " + operand);
        }
        super.visitInsn(Opcodes.DUP);
        super.visitLdcInsn(typeSize);
        super.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeAccounterTypeName, "recordArrayAllocation", "(II)V", true);
        super.visitIntInsn(opcode, operand);
    }

    /**
     * This method is called when visiting an opcode with a single operand, that
     * is a type (represented here as a String).
     * <p>
     * For our purposes this is either a NEW opcode or a ANEWARRAY
     *
     * @param opcode
     * @param type
     */
    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        // opcode is either NEW - recordAllocation:(Ljava/lang/String;)V
        // or ANEWARRAY - recordArrayAllocation:(Ljava/lang/String;I)V
        switch (opcode) {
            case Opcodes.NEW:
                super.visitLdcInsn(type);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeAccounterTypeName, "recordAllocation", "(Ljava/lang/String;)V", true);
                break;
            case Opcodes.ANEWARRAY:
                super.visitInsn(Opcodes.DUP);
                super.visitLdcInsn(8);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeAccounterTypeName, "recordArrayAllocation", "(II)V", true);
                break;
        }

        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        super.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeAccounterTypeName, "recordJump", "()V", true);
        super.visitJumpInsn(opcode, label);
    }

    /**
     * Visits a method instruction. We add accounting information to prevent runaway
     * method calls. The case of INVOKEDYNAMIC is handled by the visitInvokeDynamicInsn
     * method, but that opcode is disallowed by the whitelisting anyway.
     *
     * @param opcode
     * @param owner
     * @param name
     * @param desc
     * @param itf
     */
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {

        switch (opcode) {
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKEINTERFACE:
                super.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeAccounterTypeName, "recordMethodCall", "()V", itf);
                // If this is in the packages that are sandboxed, rewrite the link
                final String sandboxedOwner = Utils.sandboxInternalTypeName(owner);
                super.visitMethodInsn(opcode, sandboxedOwner, name, desc, itf);
                break;
            default:
                throw new IllegalStateException("Unexpected opcode: " + opcode + " from ASM when expecting an INVOKE");
        }
    }
}
