package net.corda.sandbox.rules.implementation.instrumentation

import net.corda.sandbox.code.Emitter
import net.corda.sandbox.code.EmitterContext
import net.corda.sandbox.code.Instruction
import net.corda.sandbox.code.instructions.MemberAccessInstruction

/**
 * Emitter that will instrument the byte code such that all method invocations get recorded.
 */
@Suppress("unused")
class TraceInvocations : Emitter {

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is MemberAccessInstruction && instruction.isMethod) {
            invokeInstrumenter("recordInvocation", "()V")
        }
    }

    override val isTracer: Boolean
        get() = true

}
