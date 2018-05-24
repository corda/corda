package net.corda.sandbox.rules.implementation

import net.corda.sandbox.references.Member
import net.corda.sandbox.rules.MemberRule
import net.corda.sandbox.validation.RuleContext

/**
 * Rule that checks for invalid use of finalizers.
 */
@Suppress("unused")
class DisallowFinalizerMethods : MemberRule() {

    override fun validate(context: RuleContext, member: Member) = context.validate {
        fail("Disallowed finalizer method") given ("${member.memberName}:${member.signature}" == "finalize:()V")
    }

}
