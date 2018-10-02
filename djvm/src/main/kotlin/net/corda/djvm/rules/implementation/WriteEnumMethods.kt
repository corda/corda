package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction
import org.objectweb.asm.Opcodes.*

class WriteEnumMethods : Emitter {
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
            }
        }
    }
}
