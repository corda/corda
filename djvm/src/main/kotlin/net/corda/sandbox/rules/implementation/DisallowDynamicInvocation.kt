package net.corda.sandbox.rules.implementation

import net.corda.sandbox.code.Instruction
import net.corda.sandbox.code.instructions.DynamicInvocationInstruction
import net.corda.sandbox.rules.InstructionRule
import net.corda.sandbox.validation.RuleContext

/**
 * Rule that checks for invalid dynamic invocations.
 */
@Suppress("unused")
class DisallowDynamicInvocation : InstructionRule() {

    override fun validate(context: RuleContext, instruction: Instruction) = context.validate {
        fail("Disallowed dynamic invocation in method") given (instruction is DynamicInvocationInstruction)
        // TODO Allow specific lambda and string concatenation meta-factories used by Java code itself
    }

}
