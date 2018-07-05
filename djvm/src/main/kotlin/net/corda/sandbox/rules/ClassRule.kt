package net.corda.sandbox.rules

import net.corda.sandbox.code.Instruction
import net.corda.sandbox.references.ClassRepresentation
import net.corda.sandbox.references.Member
import net.corda.sandbox.validation.RuleContext

/**
 * Representation of a rule that applies to class definitions.
 */
abstract class ClassRule : Rule {

    /**
     * Called when a class definition is visited.
     *
     * @param context The context in which the rule is to be validated.
     * @param clazz The class to apply and validate this rule against.
     */
    abstract fun validate(context: RuleContext, clazz: ClassRepresentation)

    override fun validate(context: RuleContext, clazz: ClassRepresentation?, member: Member?, instruction: Instruction?) {
        // Only run validation step if applied to the class itself.
        if (clazz != null && member == null && instruction == null) {
            validate(context, clazz)
        }
    }

}
