package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.*
import java.io.NotSerializableException

/**
 * A factory that knows how to create serialisers when there is a mismatch between the remote and local type schemas.
 */
interface EvolutionSerializerFactory {

    /**
     * Compare the given [RemoteTypeInformation] and [LocalTypeInformation], and construct (if needed) an evolution
     * serialiser that can take properties serialised in the remote schema and construct an object conformant to the local schema.
     *
     * Will return null if no evolution is necessary, because the schemas are compatible.
     */
    fun getEvolutionSerializer(
            remote: RemoteTypeInformation,
            local: LocalTypeInformation): AMQPSerializer<Any>?

    /**
     * A mapping between Java object types and their equivalent Java primitive types.
     * Predominantly for the sake of the DJVM sandbox where e.g. `char` will map to
     * sandbox.java.lang.Character instead of java.lang.Character.
     */
    val primitiveTypes: Map<Class<*>, Class<*>>
}

class EvolutionSerializationException(remoteTypeInformation: RemoteTypeInformation, reason: String)
    : NotSerializableException(
        """
        Cannot construct evolution serializer for remote type ${remoteTypeInformation.typeIdentifier.name}: $reason

        Full type information:
        ${remoteTypeInformation.prettyPrint(false)}
        """.trimIndent()
)

class DefaultEvolutionSerializerFactory(
        private val localSerializerFactory: LocalSerializerFactory,
        private val classLoader: ClassLoader,
        private val mustPreserveDataWhenEvolving: Boolean,
        override val primitiveTypes: Map<Class<*>, Class<*>>,
        private val baseTypes: BaseLocalTypes
): EvolutionSerializerFactory {
    // Invert the "primitive -> boxed primitive" mapping.
    private val primitiveBoxedTypes: Map<Class<*>, Class<*>>
        = primitiveTypes.entries.associateBy(Map.Entry<Class<*>,Class<*>>::value, Map.Entry<Class<*>,Class<*>>::key)

    override fun getEvolutionSerializer(remote: RemoteTypeInformation,
                                        local: LocalTypeInformation): AMQPSerializer<Any>? =
            when(remote) {
                is RemoteTypeInformation.Composable ->
                    if (local is LocalTypeInformation.Composable) remote.getEvolutionSerializer(local)
                    else null
                is RemoteTypeInformation.AnEnum ->
                    if (local is LocalTypeInformation.AnEnum) remote.getEvolutionSerializer(local)
                    else null
                else -> null
            }
    
    private fun RemoteTypeInformation.Composable.getEvolutionSerializer(
            localTypeInformation: LocalTypeInformation.Composable): AMQPSerializer<Any>? {
        // The no-op case: although the fingerprints don't match for some reason, we have compatible signatures.
        // This might happen because of inconsistent type erasure, changes to the behaviour of the fingerprinter,
        // or changes to the type itself - such as adding an interface - that do not change its serialisation/deserialisation
        // signature.
        if (propertyNamesMatch(localTypeInformation)) {
            // Make sure types are assignment-compatible, and return the local serializer for the type.
            validateCompatibility(localTypeInformation)
            return null
        }

        // Failing that, we have to create an evolution serializer.
        val bestMatchEvolutionConstructor = findEvolverConstructor(localTypeInformation.evolutionConstructors, properties)
        val constructorForEvolution = bestMatchEvolutionConstructor?.constructor ?: localTypeInformation.constructor
        val evolverProperties = bestMatchEvolutionConstructor?.properties ?: localTypeInformation.properties

        validateEvolvability(evolverProperties)

        return buildComposableEvolutionSerializer(localTypeInformation, constructorForEvolution, evolverProperties)
    }

    private fun RemoteTypeInformation.Composable.propertyNamesMatch(localTypeInformation: LocalTypeInformation.Composable): Boolean =
            properties.keys == localTypeInformation.properties.keys

    private fun RemoteTypeInformation.Composable.validateCompatibility(localTypeInformation: LocalTypeInformation.Composable) {
        properties.asSequence().zip(localTypeInformation.properties.values.asSequence()).forEach { (remote, localProperty) ->
            val (name, remoteProperty) = remote
            val localClass = localProperty.type.observedType.asClass()
            val remoteClass = remoteProperty.type.typeIdentifier.getLocalType(classLoader).asClass()

            if (!localClass.isAssignableFrom(remoteClass) && remoteClass != primitiveTypes[localClass]) {
                throw EvolutionSerializationException(this,
                        "Local type $localClass of property $name is not assignable from remote type $remoteClass")
            }
        }
    }

    // Find the evolution constructor with the highest version number whose parameters are all assignable from the
    // provided property types.
    private fun findEvolverConstructor(constructors: List<EvolutionConstructorInformation>,
                                       properties: Map<String, RemotePropertyInformation>): EvolutionConstructorInformation? {
        val propertyTypes = properties.mapValues { (_, info) -> info.type.typeIdentifier.getLocalType(classLoader).asClass() }

        // Evolver constructors are listed in ascending version order, so we just want the last that matches.
        return constructors.lastOrNull { (_, evolverProperties) ->
            // We have a match if all mandatory evolver properties have a type-compatible property in the remote type.
            evolverProperties.all { (name, evolverProperty) ->
                val propertyType = propertyTypes[name]
                if (propertyType == null) {
                    !evolverProperty.isMandatory
                } else {
                    // Check that we can assign the remote property value to its local equivalent.
                    // This includes assigning a primitive type to its equivalent "boxed" type.
                    val evolverPropertyType = evolverProperty.type.observedType.asClass()
                    evolverPropertyType.isAssignableFrom(propertyType)
                        || (propertyType.isPrimitive && evolverPropertyType.isAssignableFrom(boxed(propertyType)))
                        || (evolverPropertyType.isPrimitive && boxed(evolverPropertyType).isAssignableFrom(propertyType))
                }
            }
        }
    }

    private fun boxed(primitiveType: Class<*>): Class<*> {
        return primitiveBoxedTypes[primitiveType] ?: throw IllegalStateException("Unknown primitive type '$primitiveType'")
    }

    private fun RemoteTypeInformation.Composable.validateEvolvability(localProperties: Map<PropertyName, LocalPropertyInformation>) {
        val remotePropertyNames = properties.keys
        val localPropertyNames = localProperties.keys
        val newProperties = localPropertyNames - remotePropertyNames

        // Check mandatory-ness of constructor-set properties.
        newProperties.forEach { propertyName ->
            if (localProperties[propertyName]!!.mustBeProvided) throw EvolutionSerializationException(
                    this,
                    "Mandatory property $propertyName of local type is not present in remote type. " +
                    "This implies the type has not evolved in a backwards compatible way. " +
                    "Consider making $propertyName nullable in the newer version of this type.")
        }
    }

    private val LocalPropertyInformation.mustBeProvided: Boolean get() = when(this) {
        is LocalPropertyInformation.ConstructorPairedProperty -> isMandatory
        is LocalPropertyInformation.PrivateConstructorPairedProperty -> isMandatory
        else -> false
    }

    private fun RemoteTypeInformation.AnEnum.getEvolutionSerializer(
            localTypeInformation: LocalTypeInformation.AnEnum): AMQPSerializer<Any>? {
        if (members == localTypeInformation.members) return null

        val remoteTransforms = transforms
        val localTransforms = localTypeInformation.transforms
        val transforms = if (remoteTransforms.size > localTransforms.size) remoteTransforms else localTransforms

        val localOrdinals = localTypeInformation.members.mapIndexed { ord, member -> member to ord }.toMap()
        val remoteOrdinals = members.mapIndexed { ord, member -> member to ord }.toMap()
        val rules = transforms.defaults + transforms.renames

        // We just trust our transformation rules not to contain cycles here.
        tailrec fun findLocal(remote: String): String =
            if (remote in localOrdinals.keys) remote
            else localTypeInformation.fallbacks[remote] ?: findLocal(rules[remote] ?: throw EvolutionSerializationException(
                this,
                "Cannot resolve local enum member $remote to a member of ${localOrdinals.keys} using rules $rules"
            ))

        val conversions = members.associate { it to findLocal(it) }
        val convertedOrdinals = remoteOrdinals.asSequence().map { (member, ord) -> ord to conversions[member]!! }.toMap()

        if (constantsAreReordered(localOrdinals, convertedOrdinals)) throw EvolutionSerializationException(this,
                "Constants have been reordered, additions must be appended to the end")

        return EnumEvolutionSerializer(localTypeInformation.observedType, localSerializerFactory, baseTypes, conversions, localOrdinals)
    }

    private fun constantsAreReordered(localOrdinals: Map<String, Int>, convertedOrdinals: Map<Int, String>): Boolean =
        if (localOrdinals.size <= convertedOrdinals.size) {
            localOrdinals.any { (name, ordinal) -> convertedOrdinals[ordinal] != name }
        } else convertedOrdinals.any { (ordinal, name) -> localOrdinals[name] != ordinal }

    private fun RemoteTypeInformation.Composable.buildComposableEvolutionSerializer(
            localTypeInformation: LocalTypeInformation.Composable,
            constructor: LocalConstructorInformation,
            properties: Map<String, LocalPropertyInformation>): AMQPSerializer<Any> =
        EvolutionObjectSerializer.make(
                    localTypeInformation,
                    this,
                    constructor,
                    properties,
                    classLoader,
                    mustPreserveDataWhenEvolving)
}