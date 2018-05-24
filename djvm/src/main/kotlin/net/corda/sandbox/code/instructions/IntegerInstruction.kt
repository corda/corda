package net.corda.sandbox.code.instructions

import net.corda.sandbox.code.Instruction

/**
 * Instruction with a single, constant integer operand.
 *
 * @property operand The integer operand.
 */
class IntegerInstruction(
        operation: Int,
        val operand: Int
) : Instruction(operation)