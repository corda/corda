package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction
import net.corda.djvm.formatting.MemberFormatter
import org.objectweb.asm.Opcodes.*
import sandbox.net.corda.djvm.rules.RuleViolationError

/**
 * Some non-deterministic APIs belong to pinned classes and so cannot be stubbed out.
 * Replace their invocations with exceptions instead.
 */
class DisallowNonDeterministicMethods : Emitter {

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is MemberAccessInstruction && isForbidden(instruction)) {
            when (instruction.operation) {
                INVOKEVIRTUAL, INVOKESPECIAL -> {
                    throwException<RuleViolationError>("Disallowed reference to API; ${memberFormatter.format(instruction.member)}")
                    preventDefault()
                }
            }
        }
    }

    private fun isClassReflection(instruction: MemberAccessInstruction): Boolean =
            (instruction.owner == "java/lang/Class") && (
                    ((instruction.memberName == "newInstance" && instruction.signature == "()Ljava/lang/Object;")
                            || instruction.signature.contains("Ljava/lang/reflect/"))
                    )

    private fun isClassLoading(instruction: MemberAccessInstruction): Boolean =
            (instruction.owner == "java/lang/ClassLoader") && instruction.memberName in CLASSLOADING_METHODS

    private fun isObjectMonitor(instruction: MemberAccessInstruction): Boolean =
            (instruction.signature == "()V" && instruction.memberName in MONITOR_METHODS)
                    || (instruction.memberName == "wait" && (instruction.signature == "(J)V" || instruction.signature == "(JI)V"))

    private fun isForbidden(instruction: MemberAccessInstruction): Boolean
            = instruction.isMethod && (isClassReflection(instruction) || isObjectMonitor(instruction) || isClassLoading(instruction))

    private val memberFormatter = MemberFormatter()

    private companion object {
        private val MONITOR_METHODS = setOf("notify", "notifyAll", "wait")
        private val CLASSLOADING_METHODS = setOf("defineClass", "loadClass", "findClass")
    }
}