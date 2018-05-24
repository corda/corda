package net.corda.sandbox.code.instructions

import net.corda.sandbox.code.Instruction
import org.objectweb.asm.Opcodes

/**
 * Instruction that, surprise surprise (!), does nothing!
 */
open class NoOperationInstruction : Instruction(Opcodes.NOP)