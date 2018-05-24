package net.corda.sandbox.rules.implementation

import net.corda.sandbox.references.Member
import net.corda.sandbox.rules.MemberRule
import net.corda.sandbox.validation.RuleContext
import java.lang.reflect.Modifier

/**
 * Rule that checks for invalid use of native methods.
 */
@Suppress("unused")
class DisallowNativeMethods : MemberRule() {

    override fun validate(context: RuleContext, member: Member) = context.validate {
        fail("Disallowed native method") given Modifier.isNative(member.access)
    }

}
