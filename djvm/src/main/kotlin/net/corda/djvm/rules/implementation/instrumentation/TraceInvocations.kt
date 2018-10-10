package net.corda.djvm.rules.implementation.instrumentation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction

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
