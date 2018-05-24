package net.corda.sandbox.rules.implementation

import net.corda.sandbox.code.Instruction
import net.corda.sandbox.code.Instruction.Companion.OP_BREAKPOINT
import net.corda.sandbox.rules.InstructionRule
import net.corda.sandbox.validation.RuleContext

/**
 * Rule that checks for invalid breakpoint instructions.
 */
@Suppress("unused")
class DisallowBreakpoints : InstructionRule() {

    override fun validate(context: RuleContext, instruction: Instruction) = context.validate {
        fail("Disallowed breakpoint in method") given (instruction.operation == OP_BREAKPOINT)
    }

}
