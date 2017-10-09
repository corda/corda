package net.corda.sandbox.visitors;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * MethodVisitor that is a complete no-op. MethodVisitor is abstract, so we have to extend it
 */
class DefinitelyDisallowedMethodVisitor extends MethodVisitor {

    DefinitelyDisallowedMethodVisitor(MethodVisitor baseMethodVisitor) {
        super(Opcodes.ASM5, baseMethodVisitor);
    }

}
