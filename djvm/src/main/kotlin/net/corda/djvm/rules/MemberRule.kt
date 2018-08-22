package net.corda.djvm.rules

import net.corda.djvm.code.Instruction
import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.Member
import net.corda.djvm.validation.RuleContext

/**
 * Representation of a rule that applies to member definitions.
 */
abstract class MemberRule : Rule {

    /**
     * Called when a member definition is visited.
     *
     * @param context The context in which the rule is to be validated.
     * @param member The class member to apply and validate this rule against.
     */
    abstract fun validate(context: RuleContext, member: Member)

    override fun validate(context: RuleContext, clazz: ClassRepresentation?, member: Member?, instruction: Instruction?) {
        // Only run validation step if applied to the class member itself.
        if (clazz != null && member != null && instruction == null) {
            validate(context, member)
        }
    }

}
