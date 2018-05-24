package net.corda.sandbox.code.instructions

import net.corda.sandbox.code.Instruction
import org.objectweb.asm.Opcodes

/**
 * Dynamic invocation instruction.
 *
 * @property memberName The name of the method to invoke.
 * @property signature The function signature of the method being invoked.
 * @property numberOfArguments The number of arguments to pass to the target.
 * @property returnsValueOrReference False if the target returns `void`, or true if it returns a value or a reference.
 */
@Suppress("MemberVisibilityCanBePrivate")
class DynamicInvocationInstruction(
        val memberName: String,
        val signature: String,
        val numberOfArguments: Int,
        val returnsValueOrReference: Boolean
) : Instruction(Opcodes.INVOKEDYNAMIC)
