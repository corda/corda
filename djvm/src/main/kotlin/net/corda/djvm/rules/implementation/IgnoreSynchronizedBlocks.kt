package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import org.objectweb.asm.Opcodes.*

/**
 * An emitter that rewrites monitoring instructions to [POP]s, as these replacements will remove
 * the object references that [MONITORENTER] and [MONITOREXIT] anticipate to be on the stack.
 */
class IgnoreSynchronizedBlocks : Emitter {

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
