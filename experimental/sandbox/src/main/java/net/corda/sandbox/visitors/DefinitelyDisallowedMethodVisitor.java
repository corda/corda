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
