package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction

/**
 * Some whitelisted functions have [java.lang.String] arguments, so we
 * need to unwrap the [sandbox.java.lang.String] object before invoking.
 *
 * There are lots of rabbits in this hole because method arguments are
 * theoretically arbitrary. However, in practice WE control the whitelist.
 */
class ArgumentUnwrapper : Emitter {
    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is MemberAccessInstruction && context.whitelist.matches(instruction.owner)) {
            fun unwrapString() = invokeStatic("sandbox/java/lang/String", "fromDJVM", "(Lsandbox/java/lang/String;)Ljava/lang/String;")

            if (hasStringArgument(instruction)) {
                unwrapString()
            } else if (instruction.owner == "java/lang/Class" && instruction.signature.startsWith("(Ljava/lang/String;ZLjava/lang/ClassLoader;)")) {
                /**
                 * [kotlin.jvm.internal.Intrinsics.checkHasClass] invokes [Class.forName], so I'm
                 * adding support for both of this function's variants. For now.
                 */
                raiseThirdWordToTop()
                unwrapString()
                sinkTopToThirdWord()
            }
        }
    }

    private fun hasStringArgument(method: MemberAccessInstruction) = method.signature.contains("Ljava/lang/String;)")
}