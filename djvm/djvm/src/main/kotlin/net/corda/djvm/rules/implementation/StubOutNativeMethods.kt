package net.corda.djvm.rules.implementation

import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.code.EmitterModule
import net.corda.djvm.code.MemberDefinitionProvider
import net.corda.djvm.references.Member
import org.objectweb.asm.Opcodes.*
import sandbox.net.corda.djvm.rules.RuleViolationError
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

    private fun writeExceptionMethodBody(emitter: EmitterModule): Unit = with(emitter) {
        lineNumber(0)
        throwException<RuleViolationError>("Native method has been deleted")
    }

    private fun writeStubMethodBody(emitter: EmitterModule): Unit = with(emitter) {
        returnVoid()
    }

    private fun isForStubbing(member: Member): Boolean = member.signature == "()V" && member.memberName == "registerNatives"

    private fun isNative(member: Member): Boolean = Modifier.isNative(member.access)
}
