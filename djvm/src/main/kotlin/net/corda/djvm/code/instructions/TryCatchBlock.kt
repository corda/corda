package net.corda.djvm.code.instructions

import org.objectweb.asm.Label

/**
 * Try-catch block.
 *
 * @property typeName The type of the exception being caught.
 * @property handler The label of the exception handler.
 */
class TryCatchBlock(
        val typeName: String,
        val handler: Label
) : NoOperationInstruction()
