package net.corda.djvm.rules.implementation

import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.code.MemberDefinitionProvider
import net.corda.djvm.code.ruleViolationError
import net.corda.djvm.references.Member
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*

/**
 * Replace reflection APIs with stubs that throw exceptions. Only for unpinned classes.
 */
class StubOutReflectionMethods : MemberDefinitionProvider {

    override fun define(context: AnalysisRuntimeContext, member: Member): Member = when {
        isConcreteApi(member) && isReflection(member) -> member.copy(body = member.body + ::writeMethodBody)
        else -> member
    }

    private fun writeMethodBody(mv: MethodVisitor): Unit = with(mv) {
        val throwEx = Label()
        visitLabel(throwEx)
        visitLineNumber(0, throwEx)
        visitTypeInsn(NEW, ruleViolationError)
        visitInsn(DUP)
        visitLdcInsn("Disallowed reference to reflection API")
        visitMethodInsn(INVOKESPECIAL, ruleViolationError, "<init>", "(Ljava/lang/String;)V", false)
        visitInsn(ATHROW)
    }

    // The method must be public and with a Java implementation.
    private fun isConcreteApi(member: Member): Boolean = member.access and (ACC_PUBLIC or ACC_ABSTRACT or ACC_NATIVE) == ACC_PUBLIC

    private fun isReflection(member: Member): Boolean {
        return member.className.startsWith("java/lang/reflect/")
               || member.className.startsWith("java/lang/invoke/")
               || member.className.startsWith("sun/reflect/")
               || member.className == "sun/misc/Unsafe"
               || member.className == "sun/misc/VM"
    }
}
