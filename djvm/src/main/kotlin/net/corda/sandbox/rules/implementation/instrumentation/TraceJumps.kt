package net.corda.sandbox.rules.implementation.instrumentation

import net.corda.sandbox.code.Emitter
import net.corda.sandbox.code.EmitterContext
import net.corda.sandbox.code.Instruction
import net.corda.sandbox.code.instructions.BranchInstruction

/**
 * Emitter that will instrument the byte code such that all jumps get recorded.
 */
@Suppress("unused")
class TraceJumps : Emitter {

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is BranchInstruction) {
            invokeInstrumenter("recordJump", "()V")
        }
    }

    override val isTracer: Boolean
        get() = true

}
