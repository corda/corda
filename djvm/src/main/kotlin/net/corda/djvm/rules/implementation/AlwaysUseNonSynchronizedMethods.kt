package net.corda.djvm.rules.implementation

import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.code.MemberDefinitionProvider
import net.corda.djvm.references.EntityWithAccessFlag
import net.corda.djvm.references.Member
import net.corda.djvm.rules.MemberRule
import net.corda.djvm.validation.RuleContext
import org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED
import java.lang.reflect.Modifier

/**
 * Definition provider that ensures that all methods are non-synchronized in the sandbox.
 */
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
