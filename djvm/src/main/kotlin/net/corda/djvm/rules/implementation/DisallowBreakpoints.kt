package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Instruction
import net.corda.djvm.code.Instruction.Companion.OP_BREAKPOINT
import net.corda.djvm.rules.InstructionRule
import net.corda.djvm.validation.RuleContext

/**
 * Rule that checks for invalid breakpoint instructions.
 */
@Suppress("unused")
class DisallowBreakpoints : InstructionRule() {

    override fun validate(context: RuleContext, instruction: Instruction) = context.validate {
        fail("Disallowed breakpoint in method") given (instruction.operation == OP_BREAKPOINT)
    }

}
