package net.corda.djvm.rules.implementation.instrumentation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction

/**
 * [java.lang.Object] has no [toDJVMString] method, so always invoke
 * [sandbox.java.lang.String.toDJVM] explicitly afterwards.
 */
class ToDJVMStringWrapper : Emitter {
    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is MemberAccessInstruction
                && instruction.owner == "java/lang/Object"
                && instruction.memberName == "toString"
                && instruction.signature == "()Ljava/lang/String;") {
            preventDefault()
            invokeVirtual(instruction.owner, instruction.memberName, instruction.signature)
            invokeStatic("sandbox/java/lang/String", "toDJVM", "(Ljava/lang/String;)Lsandbox/java/lang/String;")
        }
    }
}