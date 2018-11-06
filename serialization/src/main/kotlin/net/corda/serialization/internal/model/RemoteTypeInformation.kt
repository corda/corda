package net.corda.serialization.internal.model

typealias TypeDescriptor = String

data class RemotePropertyInformation(val type: RemoteTypeInformation, val isMandatory: Boolean)

sealed class RemoteTypeInformation {

    abstract val typeDescriptor: TypeDescriptor
    abstract val typeIdentifier: TypeIdentifier

    fun prettyPrint(simplifyClassNames: Boolean = true, indent: Int = 0): String {
        return when (this) {
            is RemoteTypeInformation.AnInterface ->
                typeIdentifier.prettyPrint() + printInterfaces(interfaces, simplifyClassNames)
            is RemoteTypeInformation.Composable -> typeIdentifier.prettyPrint(simplifyClassNames) +
                    printInterfaces(interfaces, simplifyClassNames) +
                    printProperties(properties, simplifyClassNames, indent + 1)
            is RemoteTypeInformation.AnEnum -> typeIdentifier.prettyPrint(simplifyClassNames) +
                    members.joinToString("|", "(", ")")
            else -> typeIdentifier.prettyPrint(simplifyClassNames)
        }
    }

    private fun printInterfaces(interfaces: List<RemoteTypeInformation>, simplifyClassNames: Boolean) =
            if (interfaces.isEmpty()) ""
            else interfaces.joinToString(", ", ": ", "") {
                it.typeIdentifier.prettyPrint(simplifyClassNames)
            }

    private fun printProperties(properties: Map<String, RemotePropertyInformation>, simplifyClassNames: Boolean, indent: Int) =
            properties.entries.sortedBy { it.key }.joinToString("\n", "\n", "") {
                it.prettyPrint(simplifyClassNames, indent)
            }

    private fun Map.Entry<String, RemotePropertyInformation>.prettyPrint(simplifyClassNames: Boolean, indent: Int): String =
            "  ".repeat(indent) + key +
                    (if(!value.isMandatory) " (optional)" else "") +
                    ": " + value.type.prettyPrint(simplifyClassNames, indent)
    //endregion

    object Unknown : RemoteTypeInformation() {
        override val typeDescriptor = "?"
        override val typeIdentifier = TypeIdentifier.UnknownType
    }

    object Top : RemoteTypeInformation() {
        override val typeDescriptor = "*"
        override val typeIdentifier = TypeIdentifier.TopType
    }

    data class Cycle(override val typeIdentifier: TypeIdentifier, private val _follow: () -> RemoteTypeInformation) : RemoteTypeInformation() {
        override val typeDescriptor = typeIdentifier.name
        val follow: RemoteTypeInformation get() = _follow()

        override fun equals(other: Any?): Boolean = other is Cycle && other.typeIdentifier == typeIdentifier
        override fun hashCode(): Int = typeIdentifier.hashCode()
        override fun toString(): String = "Cycle($typeIdentifier)"
    }

    data class Unparameterised(override val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier) : RemoteTypeInformation()
    data class Parameterised(override val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier, val typeParameters: List<RemoteTypeInformation>) : RemoteTypeInformation()

    data class AnArray(override val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier, val componentType: RemoteTypeInformation) : RemoteTypeInformation()

    data class AnEnum(override val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier, val members: List<String>)
        : RemoteTypeInformation()

    data class AnInterface(override val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier, val properties: Map<String, RemotePropertyInformation>, val interfaces: List<RemoteTypeInformation>, val typeParameters: List<RemoteTypeInformation>) : RemoteTypeInformation()

    data class Composable(
            override val typeDescriptor: TypeDescriptor,
            override val typeIdentifier: TypeIdentifier,
            val properties: Map<String, RemotePropertyInformation>,
            val interfaces: List<RemoteTypeInformation>,
            val typeParameters: List<RemoteTypeInformation>) : RemoteTypeInformation()
}