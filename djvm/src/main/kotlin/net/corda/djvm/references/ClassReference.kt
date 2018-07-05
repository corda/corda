package net.corda.djvm.references

/**
 * Reference to class.
 *
 * @property className The class name.
 */
data class ClassReference(
        val className: String
) : EntityReference
