package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.ConstantInstruction

/**
 * Ensure that [String] constants loaded from the Constants
 * Pool are wrapped into [sandbox.java.lang.String].
 */
class StringConstantWrapper : Emitter {
    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is ConstantInstruction) {
            when (instruction.value) {
                is String -> {
                    invokeStatic("sandbox/java/lang/String", "toDJVM", "(Ljava/lang/String;)Lsandbox/java/lang/String;", false)
                }
            }
        }
    }
}