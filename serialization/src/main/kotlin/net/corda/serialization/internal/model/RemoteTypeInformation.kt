package net.corda.serialization.internal.model

typealias TypeDescriptor = String

/**
 * Represents a property of a remotely-defined type.
 *
 * @param type The type of the property.
 * @param isMandatory Whether the property is mandatory (i.e. non-nullable).
 */
data class RemotePropertyInformation(val type: RemoteTypeInformation, val isMandatory: Boolean)

/**
 * The [RemoteTypeInformation] extracted from a remote data source's description of its type schema captures the
 * information contained in that schema in a form similar to that of [LocalTypeInformation], but stripped of any
 * reference to local type information such as [Type]s, [Method]s, constructors and so on.
 *
 * It has two main uses:
 *
 * 1) Comparison with [LocalTypeInformation] to determine compatibility and whether type evolution should be attempted.
 * 2) Providing a specification to a [ClassCarpenter] that will synthesize a [Type] at runtime.
 *
 * A [TypeLoader] knows how to load types described by [RemoteTypeInformation], using a [ClassCarpenter] to build
 * synthetic types where needed, so that every piece of [RemoteTypeInformation] is matched to a corresponding local
 * [Type] for which [LocalTypeInformation] can be generated. Once we have both [RemoteTypeInformation] and
 * [LocalTypeInformation] in hand, we can make decisions about the compatibility between the remote and local type
 * schemas.
 *
 * In the future, it may make sense to generate type schema information by reflecting [LocalTypeInformation] into
 * [RemoteTypeInformation].
 *
 * Each piece of [RemoteTypeInformation] has both a [TypeIdentifier], which is not guaranteed to be globally uniquely
 * identifying, and a [TypeDescriptor], which is.
 *
 * [TypeIdentifier]s are not globally uniquely identifying because
 * multiple remote sources may define their own versions of the same type, with potentially different properties. However,
 * they are unique to a given message-exchange session, and are used as unique references for types within the type
 * schema associated with a given message.
 *
 * [TypeDescriptor]s are obtained by "fingerprinting" [LocalTypeInformation], and represent a hashed digest of all of
 * the information locally available about a type. If a remote [TypeDescriptor] matches that of a local type, then we
 * know that they are fully schema-compatible. However, it is possible for two types to diverge due to inconsistent
 * erasure, so that they will have different [TypeDescriptor]s, and yet represent the "same" type for purposes of
 * serialisation. In this case, we will determine compatibility based on comparison of the [RemoteTypeInformation]'s
 * type graph with that of the [LocalTypeInformation] which reflects it.
 */
sealed class RemoteTypeInformation {

    /**
     * The globally-unique [TypeDescriptor] of the represented type.
     */
    abstract val typeDescriptor: TypeDescriptor

    /**
     * The [TypeIdentifier] of the represented type.
     */
    abstract val typeIdentifier: TypeIdentifier

    /**
     * Obtain a multi-line, recursively-indented representation of this type information.
     *
     * @param simplifyClassNames By default, class names are printed as their "simple" class names, i.e. "String" instead
     * of "java.lang.String". If this is set to `false`, then the full class name will be printed instead.
     */
    fun prettyPrint(simplifyClassNames: Boolean = true): String =
            RemoteTypeInformationPrettyPrinter(simplifyClassNames).prettyPrint(this)

    /**
     * The [RemoteTypeInformation] corresponding to an unbounded wildcard ([TypeIdentifier.UnknownType])
     */
    object Unknown : RemoteTypeInformation() {
        override val typeDescriptor = "?"
        override val typeIdentifier = TypeIdentifier.UnknownType
    }

    /**
     * The [RemoteTypeInformation] corresponding to [java.lang.Object] / [Any] ([TypeIdentifier.TopType])
     */
    object Top : RemoteTypeInformation() {
        override val typeDescriptor = "*"
        override val typeIdentifier = TypeIdentifier.TopType
    }

    /**
     * The [RemoteTypeInformation] emitted if we hit a cycle while traversing the graph of related types.
     */
    data class Cycle(override val typeIdentifier: TypeIdentifier) : RemoteTypeInformation() {
        override val typeDescriptor by lazy { follow.typeDescriptor }
        lateinit var follow: RemoteTypeInformation

        override fun equals(other: Any?): Boolean = other is Cycle && other.typeIdentifier == typeIdentifier
        override fun hashCode(): Int = typeIdentifier.hashCode()
        override fun toString(): String = "Cycle($typeIdentifier)"
    }

    /**
     * Representation of a simple unparameterised type.
     */
    data class Unparameterised(override val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier) : RemoteTypeInformation()

    /**
     * Representation of a type with type parameters.
     *
     * @param typeParameters The type parameters of the type.
     */
    data class Parameterised(override val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier, val typeParameters: List<RemoteTypeInformation>) : RemoteTypeInformation()

    /**
     * Representation of an array of some other type.
     *
     * @param componentType The component type of the array.
     */
    data class AnArray(override val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier, val componentType: RemoteTypeInformation) : RemoteTypeInformation()

    /**
     * Representation of an Enum type.
     *
     * @param members The members of the enum.
     */
    data class AnEnum(override val typeDescriptor: TypeDescriptor,
                      override val typeIdentifier: TypeIdentifier,
                      val members: List<String>,
                      val transforms: EnumTransforms) : RemoteTypeInformation()

    /**
     * Representation of an interface.
     *
     * @param properties The properties (i.e. "getter" methods) of the interface.
     * @param interfaces The interfaces extended by the interface.
     * @param typeParameters The type parameters of the interface.
     */
    data class AnInterface(override val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier, val properties: Map<String, RemotePropertyInformation>, val interfaces: List<RemoteTypeInformation>, val typeParameters: List<RemoteTypeInformation>) : RemoteTypeInformation()

    /**
     * Representation of a concrete POJO-like class.
     *
     * @param properties The properties of the class.
     * @param interfaces The interfaces extended by the class.
     * @param typeParameters The type parameters of the class.
     */
    data class Composable(
            override val typeDescriptor: TypeDescriptor,
            override val typeIdentifier: TypeIdentifier,
            val properties: Map<String, RemotePropertyInformation>,
            val interfaces: List<RemoteTypeInformation>,
            val typeParameters: List<RemoteTypeInformation>) : RemoteTypeInformation()
}

private data class RemoteTypeInformationPrettyPrinter(private val simplifyClassNames: Boolean = true, private val indent: Int = 0) {

    fun prettyPrint(remoteTypeInformation: RemoteTypeInformation): String = with(remoteTypeInformation){
        when (this) {
            is RemoteTypeInformation.AnInterface -> typeIdentifier.prettyPrint(simplifyClassNames) +
                    printInterfaces(interfaces) +
                    indentAnd { printProperties(properties) }
            is RemoteTypeInformation.Composable -> typeIdentifier.prettyPrint(simplifyClassNames) +
                    printInterfaces(interfaces) +
                    indentAnd { printProperties(properties) }
            is RemoteTypeInformation.AnEnum -> typeIdentifier.prettyPrint(simplifyClassNames) +
                    members.joinToString("|", "(", ")")
            else -> typeIdentifier.prettyPrint(simplifyClassNames)
        }
    }

    private inline fun indentAnd(block: RemoteTypeInformationPrettyPrinter.() -> String) =
            copy(indent = indent + 1).block()

    private fun printInterfaces(interfaces: List<RemoteTypeInformation>) =
            if (interfaces.isEmpty()) ""
            else interfaces.joinToString(", ", ": ", "") {
                it.typeIdentifier.prettyPrint(simplifyClassNames)
            }

    private fun printProperties(properties: Map<String, RemotePropertyInformation>) =
            properties.entries.joinToString("\n", "\n", "") {
                it.prettyPrint()
            }

    private fun Map.Entry<String, RemotePropertyInformation>.prettyPrint(): String =
            "  ".repeat(indent) + key +
                    (if(!value.isMandatory) " (optional)" else "") +
                    ": " + prettyPrint(value.type)
}

