package net.corda.sandbox.code.instructions

import org.objectweb.asm.Label

/**
 * Label of a code block.
 *
 * @property label The label for the given code block.
 */
class CodeLabel(
        val label: Label
) : NoOperationInstruction()
