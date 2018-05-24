package net.corda.sandbox.rules

import net.corda.sandbox.code.Instruction
import net.corda.sandbox.references.Class
import net.corda.sandbox.references.Member
import net.corda.sandbox.validation.RuleContext

/**
 * Representation of a rule.
 */
interface Rule {

    /**
     * Validate class, member and/or instruction in the provided context.
     *
     * @param context The context in which the rule is to be validated.
     * @param clazz The class to apply and validate this rule against, if any.
     * @param member The class member to apply and validate this rule against, if any.
     * @param instruction The instruction to apply and validate this rule against, if any.
     */
    fun validate(context: RuleContext, clazz: Class?, member: Member?, instruction: Instruction?)

}
