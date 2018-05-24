package net.corda.sandbox.code.instructions

import net.corda.sandbox.code.Instruction

/**
 * Object instantiation instruction.
 *
 * @property typeName The class name of the object being instantiated.
 */
class TypeInstruction(
        operation: Int,
        val typeName: String
) : Instruction(operation)
