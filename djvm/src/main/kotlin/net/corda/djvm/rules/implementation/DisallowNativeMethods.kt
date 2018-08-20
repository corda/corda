package net.corda.djvm.rules.implementation

import net.corda.djvm.references.Member
import net.corda.djvm.rules.MemberRule
import net.corda.djvm.validation.RuleContext
import java.lang.reflect.Modifier

/**
 * Rule that checks for invalid use of native methods.
 */
class DisallowNativeMethods : MemberRule() {

    override fun validate(context: RuleContext, member: Member) = context.validate {
        fail("Disallowed native method") given Modifier.isNative(member.access)
    }

}
