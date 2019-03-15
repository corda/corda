package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import org.objectweb.asm.Opcodes.ATHROW

/**
 * Converts a [sandbox.java.lang.Throwable] into a [java.lang.Throwable]
 * so that the JVM can throw it.
 */
class ThrowExceptionWrapper : Emitter {
    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        when (instruction.operation) {
            ATHROW -> {
                invokeStatic("sandbox/java/lang/DJVM", "fromDJVM", "(Lsandbox/java/lang/Throwable;)Ljava/lang/Throwable;")
            }
        }
    }
}