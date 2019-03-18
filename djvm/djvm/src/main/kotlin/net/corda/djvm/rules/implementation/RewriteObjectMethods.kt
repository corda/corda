package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction
import org.objectweb.asm.Opcodes.*

/**
 * We cannot wrap Java array objects - and potentially others - and so these would still
 * use the non-deterministic [java.lang.Object.hashCode] by default. Therefore we intercept
 * these invocations and redirect them to our [sandbox.java.lang.DJVM] object.
 */
class RewriteObjectMethods : Emitter {
    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is MemberAccessInstruction && instruction.owner == "java/lang/Object") {
            when (instruction.operation) {
                INVOKEVIRTUAL -> if (instruction.memberName == "hashCode" && instruction.signature == "()I") {
                    invokeStatic(
                        owner = "sandbox/java/lang/DJVM",
                        name = "hashCode",
                        descriptor = "(Ljava/lang/Object;)I"
                    )
                    preventDefault()
                }
            }
        }
    }
}
