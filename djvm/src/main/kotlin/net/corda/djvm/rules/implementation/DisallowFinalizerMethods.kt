package net.corda.djvm.rules.implementation

import net.corda.djvm.references.Member
import net.corda.djvm.rules.MemberRule
import net.corda.djvm.validation.RuleContext

/**
 * Rule that checks for invalid use of finalizers.
 */
class DisallowFinalizerMethods : MemberRule() {

    override fun validate(context: RuleContext, member: Member) = context.validate {
        fail("Disallowed finalizer method") given ("${member.memberName}:${member.signature}" == "finalize:()V")
        // TODO Make this rule simply erase the finalize() method and continue execution.
    }

}
