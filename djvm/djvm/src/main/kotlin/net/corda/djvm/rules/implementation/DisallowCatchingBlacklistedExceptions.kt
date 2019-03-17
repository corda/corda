package net.corda.djvm.rules.implementation

import net.corda.djvm.code.*
import net.corda.djvm.code.instructions.CodeLabel
import net.corda.djvm.code.instructions.TryCatchBlock
import org.objectweb.asm.Label
import sandbox.net.corda.djvm.costing.ThresholdViolationError

/**
 * Rule that checks for attempted catches of [ThreadDeath], [ThresholdViolationError],
 * [StackOverflowError], [OutOfMemoryError], [Error] or [Throwable].
 */
class DisallowCatchingBlacklistedExceptions : Emitter {

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is TryCatchBlock && instruction.typeName in disallowedExceptionTypes) {
            handlers.add(instruction.handler)
        } else if (instruction is CodeLabel && isExceptionHandler(instruction.label)) {
            duplicate()
            invokeInstrumenter("checkCatch", "(Ljava/lang/Throwable;)V")
        }
    }

    private val handlers = mutableSetOf<Label>()

    private fun isExceptionHandler(label: Label) = label in handlers

    companion object {
        private val disallowedExceptionTypes = setOf(
            ruleViolationError,
            thresholdViolationError,

            /**
             * These errors indicate that the JVM is failing,
             * so don't allow these to be caught either.
             */
            "java/lang/StackOverflowError",
            "java/lang/OutOfMemoryError",

            /**
             * These are immediate super-classes for our explicit errors.
             */
            "java/lang/VirtualMachineError",
            "java/lang/ThreadDeath",

            /**
             * Any of [ThreadDeath] and [VirtualMachineError]'s throwable
             * super-classes also need explicit checking.
             */
            "java/lang/Throwable",
            "java/lang/Error"
        )

    }

    /**
     * We need to invoke this emitter before the [HandleExceptionUnwrapper]
     * so that we don't unwrap exceptions we don't want to catch.
     */
    override val priority: Int
        get() = EMIT_TRAPPING_EXCEPTIONS
}
