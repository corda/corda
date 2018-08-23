package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.CodeLabel
import net.corda.djvm.code.instructions.TryCatchBlock
import net.corda.djvm.costing.ThresholdViolationException
import net.corda.djvm.rules.InstructionRule
import net.corda.djvm.validation.RuleContext
import org.objectweb.asm.Label

/**
 * Rule that checks for attempted catches of [ThreadDeath], [ThresholdViolationException], [StackOverflowError],
 * [OutOfMemoryError], [Error] or [Throwable].
 */
class DisallowCatchingBlacklistedExceptions : InstructionRule(), Emitter {

    override fun validate(context: RuleContext, instruction: Instruction) = context.validate {
        if (instruction is TryCatchBlock) {
            val typeName = context.classModule.getFormattedClassName(instruction.typeName)
            warn("Injected runtime check for catch-block for type $typeName") given
                    (instruction.typeName in disallowedExceptionTypes)
            fail("Disallowed catch of ThreadDeath exception") given
                    (instruction.typeName == threadDeathException)
            fail("Disallowed catch of stack overflow exception") given
                    (instruction.typeName == stackOverflowException)
            fail("Disallowed catch of out of memory exception") given
                    (instruction.typeName == outOfMemoryException)
            fail("Disallowed catch of threshold violation exception") given
                    (instruction.typeName.endsWith(ThresholdViolationException::class.java.simpleName))
        }
    }

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

        private const val threadDeathException = "java/lang/ThreadDeath"
        private const val stackOverflowException = "java/lang/StackOverflowError"
        private const val outOfMemoryException = "java/lang/OutOfMemoryError"

        // Any of [ThreadDeath]'s throwable super-classes need explicit checking.
        private val disallowedExceptionTypes = setOf(
                "java/lang/Throwable",
                "java/lang/Error"
        )

    }

}
