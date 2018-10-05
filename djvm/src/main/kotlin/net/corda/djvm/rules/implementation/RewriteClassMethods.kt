package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction
import org.objectweb.asm.Opcodes.*

/**
 * The enum-related methods on [Class] all require that enums use [java.lang.Enum]
 * as their super class. So replace their all invocations with ones to equivalent
 * methods on the DJVM class that require [sandbox.java.lang.Enum] instead.
 */
class RewriteClassMethods : Emitter {
    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is MemberAccessInstruction && instruction.owner == "java/lang/Class") {
            when (instruction.operation) {
                INVOKEVIRTUAL -> if (instruction.memberName == "enumConstantDirectory" && instruction.signature == "()Ljava/util/Map;") {
                    invokeStatic(
                        owner = "sandbox/java/lang/DJVM",
                        name = "enumConstantDirectory",
                        descriptor = "(Ljava/lang/Class;)Lsandbox/java/util/Map;"
                    )
                    preventDefault()
                } else if (instruction.memberName == "isEnum" && instruction.signature == "()Z") {
                    invokeStatic(
                        owner = "sandbox/java/lang/DJVM",
                        name = "isEnum",
                        descriptor = "(Ljava/lang/Class;)Z"
                    )
                    preventDefault()
                } else if (instruction.memberName == "getEnumConstants" && instruction.signature == "()[Ljava/lang/Object;") {
                    invokeStatic(
                        owner = "sandbox/java/lang/DJVM",
                        name = "getEnumConstants",
                        descriptor = "(Ljava/lang/Class;)[Ljava/lang/Object;")
                    preventDefault()
                }

                INVOKESTATIC -> if (isClassForName(instruction)) {
                    invokeStatic(
                        owner = "sandbox/java/lang/DJVM",
                        name = "classForName",
                        descriptor = instruction.signature
                    )
                    preventDefault()
                }
            }
        }
    }

    private fun isClassForName(instruction: MemberAccessInstruction): Boolean
        = instruction.memberName == "forName" &&
            (instruction.signature == "(Ljava/lang/String;)Ljava/lang/Class;" ||
                    instruction.signature == "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;")
}
