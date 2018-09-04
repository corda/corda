package net.corda.djvm.rules.implementation.instrumentation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.IntegerInstruction
import net.corda.djvm.code.instructions.TypeInstruction
import org.objectweb.asm.Opcodes.*

/**
 * Emitter that will instrument the byte code such that all memory allocations get recorded.
 */
@Suppress("unused")
class TraceAllocations : Emitter {

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is TypeInstruction) {
            when (instruction.operation) {
                NEW -> {
                    loadConstant(context.resolve(instruction.typeName))
                    invokeInstrumenter("recordAllocation", "(Ljava/lang/String;)V")
                }
                ANEWARRAY -> {
                    duplicate() // Number of elements
                    loadConstant(context.resolve(instruction.typeName))
                    invokeInstrumenter("recordArrayAllocation", "(ILjava/lang/String;)V")
                }
            }
        } else if (instruction is IntegerInstruction && instruction.operation == NEWARRAY) {
            val typeSize: Int = when (instruction.operand) {
                T_BOOLEAN, T_BYTE -> 1
                T_SHORT, T_CHAR -> 2
                T_INT, T_FLOAT -> 4
                T_LONG, T_DOUBLE -> 8
                else -> throw IllegalStateException("Illegal operand to NEWARRAY {${instruction.operand})")
            }
            duplicate() // Number of elements
            loadConstant(typeSize) // Size of the primitive type
            invokeInstrumenter("recordArrayAllocation", "(II)V")
        }
    }

    override val isTracer: Boolean
        get() = true

}
