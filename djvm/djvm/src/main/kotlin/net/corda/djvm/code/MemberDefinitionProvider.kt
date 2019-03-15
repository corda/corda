package net.corda.djvm.code

import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.references.Member

/**
 * A member definition provider is a hook for [ClassMutator], from where one can modify the name and meta-data of
 * processed class members.
 */
interface MemberDefinitionProvider : DefinitionProvider {

    /**
     * Hook for providing modifications to a member definition.
     *
     * @param context The context in which the hook is called.
     * @param member The original member definition.
     *
     * @return The updated member definition, or [member] if no changes are desired.
     */
    fun define(context: AnalysisRuntimeContext, member: Member): Member

}
