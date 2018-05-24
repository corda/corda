package net.corda.sandbox.references

/**
 * Reference to class.
 *
 * @property className The class name.
 */
data class ClassReference(
        val className: String
) : EntityReference
