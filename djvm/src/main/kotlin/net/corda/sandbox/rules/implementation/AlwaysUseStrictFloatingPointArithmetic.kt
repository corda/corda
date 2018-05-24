package net.corda.sandbox.rules.implementation

import net.corda.sandbox.analysis.AnalysisRuntimeContext
import net.corda.sandbox.code.MemberDefinitionProvider
import net.corda.sandbox.references.EntityWithAccessFlag
import net.corda.sandbox.references.Member
import net.corda.sandbox.rules.MemberRule
import net.corda.sandbox.validation.RuleContext
import org.objectweb.asm.Opcodes.ACC_STRICT
import java.lang.reflect.Modifier

/**
 * Definition provider that ensures that all methods use strict floating-point arithmetic in the sandbox.
 */
@Suppress("unused")
class AlwaysUseStrictFloatingPointArithmetic : MemberRule(), MemberDefinitionProvider {

    override fun validate(context: RuleContext, member: Member) = context.validate {
        if (isConcrete(context.clazz)) {
            trace("Strict floating-point arithmetic will be applied") given ((member.access and ACC_STRICT) == 0)
        }
    }

    override fun define(context: AnalysisRuntimeContext, member: Member) = when {
        isConcrete(context.clazz) -> member.copy(access = member.access or ACC_STRICT)
        else -> member
    }

    private fun isConcrete(entity: EntityWithAccessFlag) = !Modifier.isAbstract(entity.access)

}
