package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction
import net.corda.djvm.formatting.MemberFormatter
import net.corda.djvm.rules.InstructionRule
import net.corda.djvm.validation.RuleContext

/**
 * Rule that checks for illegal references to reflection APIs.
 */
class DisallowReflection : InstructionRule() {

    override fun validate(context: RuleContext, instruction: Instruction) = context.validate {
        // TODO Enable controlled use of reflection APIs
        if (instruction is MemberAccessInstruction) {
            invalidReflectionUsage(instruction) given
                    ("java/lang/Class" in instruction.owner && instruction.memberName == "newInstance")
            invalidReflectionUsage(instruction) given ("java/lang/reflect" in instruction.owner)
            invalidReflectionUsage(instruction) given ("java/lang/invoke" in instruction.owner)
        }
    }

    private fun RuleContext.invalidReflectionUsage(instruction: MemberAccessInstruction) =
            this.fail("Disallowed reference to reflection API; ${memberFormatter.format(instruction.member)}")

    private val memberFormatter = MemberFormatter()

}
