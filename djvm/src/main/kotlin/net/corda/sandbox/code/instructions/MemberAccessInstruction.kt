package net.corda.sandbox.code.instructions

import net.corda.sandbox.code.Instruction
import net.corda.sandbox.references.MemberReference

/**
 * Field access and method invocation instruction.
 *
 * @property owner  The class owning the field or method.
 * @property memberName The name of the field or the method being accessed.
 * @property signature The return type of a field or function signature for a method.
 * @property ownerIsInterface If the member is a method, this is true if the owner is an interface.
 * @property isMethod Indicates whether the member is a method or a field.
 */
class MemberAccessInstruction(
        operation: Int,
        val owner: String,
        val memberName: String,
        val signature: String,
        val ownerIsInterface: Boolean = false,
        val isMethod: Boolean = false
) : Instruction(operation) {

    /**
     * The absolute name of the referenced member.
     */
    val reference = "$owner.$memberName:$signature"

    /**
     * Get a member reference representation of the target of the instruction.
     */
    val member: MemberReference
        get() = MemberReference(owner, memberName, signature)

}
