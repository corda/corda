package net.corda.djvm.code.instructions

import net.corda.djvm.code.Instruction
import org.objectweb.asm.Opcodes

class ConstantInstruction(val value: Any) : Instruction(Opcodes.LDC)