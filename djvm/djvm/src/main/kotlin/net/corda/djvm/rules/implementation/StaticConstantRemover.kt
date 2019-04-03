package net.corda.djvm.rules.implementation

import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.code.EmitterModule
import net.corda.djvm.code.MemberDefinitionProvider
import net.corda.djvm.references.Member

/**
 * Removes static constant objects that are initialised directly in the byte-code.
 * Currently, the only use-case is for re-initialising [String] fields.
 */
class StaticConstantRemover : MemberDefinitionProvider {

    override fun define(context: AnalysisRuntimeContext, member: Member): Member = when {
        isConstantField(member) -> member.copy(body = listOf(StringFieldInitializer(member)::writeInitializer), value = null)
        else -> member
    }

    private fun isConstantField(member: Member): Boolean = member.value != null && member.signature == "Ljava/lang/String;"

    class StringFieldInitializer(private val member: Member) {
        fun writeInitializer(emitter: EmitterModule): Unit = with(emitter) {
            member.value?.apply {
                loadConstant(this)
                invokeStatic("sandbox/java/lang/String", "toDJVM", "(Ljava/lang/String;)Lsandbox/java/lang/String;", false)
                putStatic(member.className, member.memberName, "Lsandbox/java/lang/String;")
            }
        }
    }
}