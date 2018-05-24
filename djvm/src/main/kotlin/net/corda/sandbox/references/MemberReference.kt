package net.corda.sandbox.references

/**
 * Reference to class member.
 *
 * @property className Class name of the owner.
 * @property memberName Name of the referenced field or method.
 * @property signature The signature of the field or method.
 */
data class MemberReference(
        override val className: String,
        override val memberName: String,
        override val signature: String
) : EntityReference, MemberInformation
