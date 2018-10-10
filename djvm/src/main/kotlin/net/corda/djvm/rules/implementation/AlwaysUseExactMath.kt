package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import org.objectweb.asm.Opcodes.*

/**
 * Use exact integer and long arithmetic where possible.
 *
 * Note: Strictly speaking, this rule is not a requirement for determinism, but we believe it helps make code more
 * robust. The outcome of enabling this rule is that arithmetical overflows for addition and multiplication operations
 * will be thrown instead of silenced.
 */
class AlwaysUseExactMath : Emitter {

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (context.clazz.name == "java/lang/Math") {
            return
        }
        when (instruction.operation) {
            IADD -> {
                invokeStatic("java/lang/Math", "addExact", "(II)I")
                preventDefault()
            }
            LADD -> {
                invokeStatic("java/lang/Math", "addExact", "(JJ)J")
                preventDefault()
            }
            IMUL -> {
                invokeStatic("java/lang/Math", "multiplyExact", "(II)I")
                preventDefault()
            }
            LMUL -> {
                invokeStatic("java/lang/Math", "multiplyExact", "(JJ)J")
                preventDefault()
            }
        }
        // TODO Add mappings for other operations, e.g., increment, negate, etc.
    }

}
