package net.corda.djvm.code.instructions

import net.corda.djvm.code.Instruction

/**
 * Instruction with a single, constant integer operand.
 *
 * @property operand The integer operand.
 */
class IntegerInstruction(
        operation: Int,
        val operand: Int
) : Instruction(operation)