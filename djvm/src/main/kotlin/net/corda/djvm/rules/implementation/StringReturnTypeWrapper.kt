package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction

/**
 * Classes which cannot be mapped into the sandbox will still return [java.lang.String]
 * from some functions, e.g. [java.lang.Object.toString]. So always explicitly invoke
 * [sandbox.java.lang.String.toDJVM] after these.
 */
class StringReturnTypeWrapper : Emitter {
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
