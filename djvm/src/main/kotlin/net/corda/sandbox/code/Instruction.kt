package net.corda.sandbox.code

import org.objectweb.asm.Opcodes

/**
 * Byte code instruction.
 *
 * @property operation The operation code, enumerated in [Opcodes].
 */
open class Instruction(
        val operation: Int
) {

    companion object {

        /**
         * Byte code for the breakpoint operation.
         */
        const val OP_BREAKPOINT: Int = 202

    }

}
