package net.corda.sandbox.rules

import net.corda.sandbox.code.Instruction
import net.corda.sandbox.references.Class
import net.corda.sandbox.references.Member
import net.corda.sandbox.validation.RuleContext

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

    override fun validate(context: RuleContext, clazz: Class?, member: Member?, instruction: Instruction?) {
        // Only run validation step if applied to the class member itself.
        if (clazz != null && member != null && instruction == null) {
            validate(context, member)
        }
    }

}
