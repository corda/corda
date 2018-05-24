package net.corda.sandbox.code.instructions

import net.corda.sandbox.code.Instruction
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

/**
 * Table switch instruction.
 *
 * @property min The minimum key value.
 * @property max The maximum key value.
 * @property defaultHandler The label of the default handler block.
 * @property handlers The labels of each of the handler blocks, where the label of the handler block for key
 * `min + i` is at index `i` in `handlers`.
 */
@Suppress("MemberVisibilityCanBePrivate")
class TableSwitchInstruction(
        val min: Int,
        val max: Int,
        val defaultHandler: Label,
        val handlers: List<Label>
) : Instruction(Opcodes.TABLESWITCH)