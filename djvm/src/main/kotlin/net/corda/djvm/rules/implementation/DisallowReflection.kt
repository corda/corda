package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction
import net.corda.djvm.formatting.MemberFormatter
import org.objectweb.asm.Opcodes.*
import sandbox.net.corda.djvm.rules.RuleViolationError

/**
 * Some reflection APIs belong to pinned classes and so cannot be stubbed out.
 * Replace their invocations with exceptions instead.
 */
class DisallowReflection : Emitter {

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is MemberAccessInstruction && isReflection(instruction)) {
            when (instruction.operation) {
                INVOKEVIRTUAL -> {
                    throwException<RuleViolationError>("Disallowed reference to reflection API; ${memberFormatter.format(instruction.member)}")
                    preventDefault()
                }
            }
        }
    }

    private fun isClassReflection(instruction: MemberAccessInstruction) : Boolean =
        (instruction.owner == "java/lang/Class") && (
            ((instruction.memberName == "newInstance" && instruction.signature == "()Ljava/lang/Object;")
              || instruction.signature.contains("Ljava/lang/reflect/"))
        )

    private fun isReflection(instruction: MemberAccessInstruction): Boolean
                  = instruction.isMethod && isClassReflection(instruction)

    private val memberFormatter = MemberFormatter()
}