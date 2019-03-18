package net.corda.djvm.code.instructions

import org.objectweb.asm.Label

open class TryBlock(
        val handler: Label,
        val typeName: String
) : NoOperationInstruction()