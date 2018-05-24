package net.corda.sandbox.rules.implementation

import net.corda.sandbox.code.Emitter
import net.corda.sandbox.code.EmitterContext
import net.corda.sandbox.code.Instruction
import net.corda.sandbox.rules.InstructionRule
import net.corda.sandbox.validation.RuleContext
import org.objectweb.asm.Opcodes.*

/**
 * Rule that warns about the use of synchronized code blocks. This class also exposes an emitter that rewrites pertinent
 * monitoring instructions to [POP]'s, as these replacements will remove the object references that [MONITORENTER] and
 * [MONITOREXIT] anticipate to be on the stack.
 */
@Suppress("unused")
class IgnoreSynchronizedBlocks : InstructionRule(), Emitter {

    override fun validate(context: RuleContext, instruction: Instruction) = context.validate {
        inform("Stripped monitoring instruction") given (instruction.operation in setOf(MONITORENTER, MONITOREXIT))
    }

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        when (instruction.operation) {
            MONITORENTER, MONITOREXIT -> {
                // Each of these instructions takes an object reference from the stack as input, so we need to pop
                // that element off to make sure that the stack is balanced.
                pop()
                preventDefault()
            }
        }
    }

}
