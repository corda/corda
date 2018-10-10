package net.corda.djvm.code.instructions

import net.corda.djvm.code.Instruction
import org.objectweb.asm.Label

/**
 * Branch instruction.
 *
 * @property label The label of the target.
 */
@Suppress("MemberVisibilityCanBePrivate")
class BranchInstruction(
        operation: Int,
        val label: Label
) : Instruction(operation)