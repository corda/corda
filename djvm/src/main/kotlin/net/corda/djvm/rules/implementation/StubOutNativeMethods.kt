package net.corda.djvm.rules.implementation

import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.code.MemberDefinitionProvider
import net.corda.djvm.code.ruleViolationError
import net.corda.djvm.references.Member
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import java.lang.reflect.Modifier

/**
 * Rule that replaces a native method with a stub that throws an exception.
 */
class StubOutNativeMethods : MemberDefinitionProvider {

    override fun define(context: AnalysisRuntimeContext, member: Member) = when {
        isNative(member) -> member.copy(
            access = member.access and ACC_NATIVE.inv(),
            body = member.body + if (isForStubbing(member)) ::writeStubMethodBody else ::writeExceptionMethodBody
        )
        else -> member
    }

    private fun writeExceptionMethodBody(mv: MethodVisitor): Unit = with(mv) {
        val throwEx = Label()
        visitLabel(throwEx)
        visitLineNumber(0, throwEx)
        visitTypeInsn(NEW, ruleViolationError)
        visitInsn(DUP)
        visitLdcInsn("Native method has been deleted")
        visitMethodInsn(INVOKESPECIAL, ruleViolationError, "<init>", "(Ljava/lang/String;)V", false)
        visitInsn(ATHROW)
    }

    private fun writeStubMethodBody(mv: MethodVisitor): Unit = with(mv) {
        visitInsn(RETURN)
    }

    private fun isForStubbing(member: Member): Boolean = member.signature == "()V" && member.memberName == "registerNatives"

    private fun isNative(member: Member): Boolean = Modifier.isNative(member.access)
}
