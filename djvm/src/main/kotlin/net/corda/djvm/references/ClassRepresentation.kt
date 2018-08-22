package net.corda.djvm.references

/**
 * Representation of a class.
 *
 * @property apiVersion The target API version for which the class was compiled.
 * @property access The access flags of the class.
 * @property name The name of the class.
 * @property superClass The name of the super-class, if any.
 * @property interfaces The names of the interfaces implemented by the class.
 * @property sourceFile The name of the compiled source file, if available.
 * @property genericsDetails Details about generics used.
 * @property members The set of fields and methods implemented in the class.
 * @property annotations The set of annotations applied to the class.
 */
data class ClassRepresentation(
        val apiVersion: Int,
        override val access: Int,
        val name: String,
        val superClass: String = "",
        val interfaces: List<String> = listOf(),
        var sourceFile: String = "",
        val genericsDetails: String = "",
        val members: MutableMap<String, Member> = mutableMapOf(),
        val annotations: MutableSet<String> = mutableSetOf()
) : EntityWithAccessFlag
