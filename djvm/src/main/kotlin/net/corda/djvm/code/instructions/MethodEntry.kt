package net.corda.djvm.code.instructions

import net.corda.djvm.references.Member

/**
 * Pseudo-instruction marking the beginning of a method.
 * @property method [Member] describing this method.
 */
class MethodEntry(val method: Member): NoOperationInstruction()