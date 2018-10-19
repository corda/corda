package net.corda.serialization.internal.model

typealias TypeDescriptor = String

data class RemotePropertyInformation(val type: RemoteTypeInformation, val isMandatory: Boolean)

data class RemoteConstructorParameterInformation(
        val name: String,
        val type: RemoteTypeInformation,
        val isMandatory: Boolean)

data class RemoteConstructorInformation(
        val parameters: List<RemoteConstructorParameterInformation>)

sealed class RemoteTypeInformation {

    abstract val typeIdentifier: TypeIdentifier

    //region Pretty printing
    /*
    fun prettyPrint(indent: Int = 0): String {
        return when (this) {
            is RemoteTypeInformation.Abstract ->
                typeIdentifier.prettyPrint() + printInterfaces(interfaces) + printProperties(properties, indent + 1)
            is RemoteTypeInformation.AnInterface ->
                typeIdentifier.prettyPrint() + printInterfaces(interfaces)
            is RemoteTypeInformation.APojo -> typeIdentifier.prettyPrint() +
                    printConstructor(constructor) +
                    printInterfaces(interfaces) +
                    printProperties(properties, indent + 1)
            else -> typeIdentifier.prettyPrint()
        }
    }

    private fun printConstructor(constructor: RemoteConstructorInformation) =
            constructor.parameters.joinToString(", ", "(", ")") {
                it.name +
                ": " + it.type.typeIdentifier.prettyPrint() +
                (if (!it.isMandatory) "?" else "")
            }

    private fun printInterfaces(interfaces: List<RemoteTypeInformation>) =
            if (interfaces.isEmpty()) ""
            else interfaces.joinToString(", ", ": ", "") { it.typeIdentifier.prettyPrint() }

    private fun printProperties(properties: Map<String, RemotePropertyInformation>, indent: Int) =
            properties.entries.sortedBy { it.key }.joinToString("\n", "\n", "") {
                it.prettyPrint(indent)
            }

    private fun Map.Entry<String, RemotePropertyInformation>.prettyPrint(indent: Int): String =
            "  ".repeat(indent) + key +
                    (if(!value.isMandatory) " (optional)" else "") +
                    ": " + value.type.prettyPrint(indent)
                    */
    //endregion

    object Unknown : RemoteTypeInformation() {
        override val typeIdentifier = TypeIdentifier.Unknown
    }

    object Any : RemoteTypeInformation() {
        override val typeIdentifier = TypeIdentifier.Top
    }

    data class Unparameterised(override val typeIdentifier: TypeIdentifier) : RemoteTypeInformation()
    data class Parameterised(override val typeIdentifier: TypeIdentifier, val typeParameters: List<RemoteTypeInformation>) : RemoteTypeInformation()

    data class AnArray(override val typeIdentifier: TypeIdentifier, val componentType: RemoteTypeInformation) : RemoteTypeInformation()

    data class AnEnum(val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier, val interfaces: List<RemoteTypeInformation>, val members: List<String>)
        : RemoteTypeInformation()

    data class AnInterface(val typeDescriptor: TypeDescriptor, override val typeIdentifier: TypeIdentifier, val interfaces: List<RemoteTypeInformation>, val typeParameters: List<RemoteTypeInformation>) : RemoteTypeInformation()

    data class APojo(
            val typeDescriptor: TypeDescriptor,
            override val typeIdentifier: TypeIdentifier,
            val properties: Map<PropertyName, RemotePropertyInformation>,
            val interfaces: List<RemoteTypeInformation>,
            val typeParameters: List<RemoteTypeInformation>) : RemoteTypeInformation()
}