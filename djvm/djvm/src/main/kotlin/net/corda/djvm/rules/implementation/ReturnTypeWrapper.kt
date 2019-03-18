package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction

/**
 * Whitelisted classes may still return [java.lang.String] from some
 * functions, e.g. [java.lang.Object.toString]. So always explicitly
 * invoke [sandbox.java.lang.String.toDJVM] after these.
 */
class ReturnTypeWrapper : Emitter {
    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is MemberAccessInstruction && context.whitelist.matches(instruction.owner)) {
            fun invokeMethod() = invokeVirtual(instruction.owner, instruction.memberName, instruction.signature)

            if (hasStringReturnType(instruction)) {
                preventDefault()
                invokeMethod()
                invokeStatic("sandbox/java/lang/String", "toDJVM", "(Ljava/lang/String;)Lsandbox/java/lang/String;")
            }
        }
    }

    private fun hasStringReturnType(method: MemberAccessInstruction) = method.signature.endsWith(")Ljava/lang/String;")
}