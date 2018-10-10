package net.corda.djvm.rules.implementation.instrumentation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import org.objectweb.asm.Opcodes.ATHROW

/**
 * Emitter that will instrument the byte code such that all throws get recorded.
 */
@Suppress("unused")
class TraceThrows : Emitter {

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction.operation == ATHROW) {
            invokeInstrumenter("recordThrow", "()V")
        }
    }

    override val isTracer: Boolean
        get() = true

}
