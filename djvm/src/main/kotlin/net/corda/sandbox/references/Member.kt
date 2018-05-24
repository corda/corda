package net.corda.sandbox.references

/**
 * Representation of a class member.
 *
 * @property access The access flags of the member.
 * @property className The name of the owning class.
 * @property memberName The name of the member.
 * @property signature The signature of the member.
 * @property genericsDetails Details about generics used.
 * @property annotations The names of the annotations the member is attributed.
 * @property exceptions The names of the exceptions that the member can throw.
 * @property value The default value of a field.
 */
data class Member(
        override val access: Int,
        override val className: String,
        override val memberName: String,
        override val signature: String,
        val genericsDetails: String,
        val annotations: MutableSet<String> = mutableSetOf(),
        val exceptions: MutableSet<String> = mutableSetOf(),
        val value: Any? = null
) : MemberInformation, EntityWithAccessFlag
