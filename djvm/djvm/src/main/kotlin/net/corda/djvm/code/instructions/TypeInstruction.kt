package net.corda.djvm.code.instructions

import net.corda.djvm.code.Instruction

/**
 * Object instantiation instruction.
 *
 * @property typeName The class name of the object being instantiated.
 */
class TypeInstruction(
        operation: Int,
        val typeName: String
) : Instruction(operation)
