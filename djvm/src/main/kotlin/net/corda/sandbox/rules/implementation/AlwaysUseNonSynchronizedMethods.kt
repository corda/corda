package net.corda.sandbox.rules.implementation

import net.corda.sandbox.analysis.AnalysisRuntimeContext
import net.corda.sandbox.code.MemberDefinitionProvider
import net.corda.sandbox.references.EntityWithAccessFlag
import net.corda.sandbox.references.Member
import net.corda.sandbox.rules.MemberRule
import net.corda.sandbox.validation.RuleContext
import org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED
import java.lang.reflect.Modifier

/**
 * Definition provider that ensures that all methods are non-synchronized in the sandbox.
 */
@Suppress("unused")
class AlwaysUseNonSynchronizedMethods : MemberRule(), MemberDefinitionProvider {

    override fun validate(context: RuleContext, member: Member) = context.validate {
        if (isConcrete(context.clazz)) {
            trace("Synchronization specifier will be ignored") given ((member.access and ACC_SYNCHRONIZED) == 0)
        }
    }

    override fun define(context: AnalysisRuntimeContext, member: Member) = when {
        isConcrete(context.clazz) -> member.copy(access = member.access and ACC_SYNCHRONIZED.inv())
        else -> member
    }

    private fun isConcrete(entity: EntityWithAccessFlag) = !Modifier.isAbstract(entity.access)

}
