package net.corda.djvm.code.instructions

import net.corda.djvm.code.Instruction
import org.objectweb.asm.Opcodes

/**
 * Instruction that, surprise surprise (!), does nothing!
 */
open class NoOperationInstruction : Instruction(Opcodes.NOP)