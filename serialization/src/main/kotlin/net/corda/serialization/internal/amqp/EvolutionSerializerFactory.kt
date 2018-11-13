package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.*
import java.io.NotSerializableException

interface EvolutionSerializerFactory {
    fun getEvolutionSerializer(
            remoteTypeInformation: RemoteTypeInformation,
            localTypeInformation: LocalTypeInformation): AMQPSerializer<Any>
}

class EvolutionSerializationException(remoteTypeInformation: RemoteTypeInformation, reason: String)
    : NotSerializableException(
        """
            Cannot construct evolution serializer for remote type ${remoteTypeInformation.prettyPrint(false)}

            $reason
        """.trimIndent()
)

class DefaultEvolutionSerializerFactory(
        private val localSerializerFactory: LocalSerializerFactory,
        private val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
        private val classLoader: ClassLoader,
        private val mustPreserveData: Boolean): EvolutionSerializerFactory {

    override fun getEvolutionSerializer(remoteTypeInformation: RemoteTypeInformation,
                                        localTypeInformation: LocalTypeInformation): AMQPSerializer<Any> {
        return getClassifiedSerializer(remoteTypeInformation, localTypeInformation)
    }
    
    private fun getClassifiedSerializer(
            remoteTypeInformation: RemoteTypeInformation,
            localTypeInformation: LocalTypeInformation): AMQPSerializer<Any> {
        val local = (localTypeInformation as? LocalTypeInformation.Cycle)?.follow ?: localTypeInformation

        val serializerForTypeDescriptor = when(remoteTypeInformation) {
            is RemoteTypeInformation.Composable ->
                if (local is LocalTypeInformation.Composable) classifyComposable(remoteTypeInformation, local)
                else throw EvolutionSerializationException(remoteTypeInformation,
                        "Local type ${local.typeIdentifier.prettyPrint(false)} is not composable")
            is RemoteTypeInformation.AnEnum ->
                if (local is LocalTypeInformation.AnEnum) classifyEnum(remoteTypeInformation, local)
                else throw EvolutionSerializationException(remoteTypeInformation,
                        "Local type ${local.typeIdentifier.prettyPrint(false)} is not an enum")
            else -> null
        }

        if (serializerForTypeDescriptor != null) {
            descriptorBasedSerializerRegistry[remoteTypeInformation.typeDescriptor] = serializerForTypeDescriptor
        }

        return serializerForTypeDescriptor ?: localSerializerFactory.get(localTypeInformation)
    }
    
    private fun classifyComposable(
            remoteTypeInformation: RemoteTypeInformation.Composable,
            localTypeInformation: LocalTypeInformation.Composable): AMQPSerializer<Any> {
        // The no-op case: although the fingerprints don't match for some reason, we have compatible signatures.
        if (remoteTypeInformation.propertyNamesMatch(localTypeInformation)) {
            // Make sure types are assignment-compatible, and return the local serializer for the type.
            remoteTypeInformation.validateCompatibility(localTypeInformation)
            return localSerializerFactory.get(localTypeInformation)
        }

        // Failing that, we have to create an evolution serializer.
        val bestMatchEvolutionConstructor = findEvolverConstructor(localTypeInformation.evolverConstructors, remoteTypeInformation.properties)
        val constructorForEvolution = bestMatchEvolutionConstructor?.constructor ?: localTypeInformation.constructor
        val evolverProperties = bestMatchEvolutionConstructor?.properties ?: localTypeInformation.properties

        remoteTypeInformation.validateEvolvability(evolverProperties)

        return buildComposableEvolutionSerializer(
                        remoteTypeInformation,
                        localTypeInformation,
                        constructorForEvolution,
                        evolverProperties)
    }

    private fun RemoteTypeInformation.Composable.propertyNamesMatch(localTypeInformation: LocalTypeInformation.Composable): Boolean =
            properties.keys == localTypeInformation.properties.keys

    private fun RemoteTypeInformation.Composable.validateCompatibility(localTypeInformation: LocalTypeInformation.Composable) {
        properties.asSequence().zip(localTypeInformation.properties.values.asSequence()).forEach { (remote, localProperty) ->
            val (name, remoteProperty) = remote
            val localClass = localProperty.type.observedType.asClass()
            val remoteClass = remoteProperty.type.typeIdentifier.getLocalType(classLoader).asClass()

            if (!localClass.isAssignableFrom(remoteClass)) {
                throw EvolutionSerializationException(this,
                        "Local type $localClass of property $name is not assignable from remote type $remoteClass")
            }
        }
    }

    // Find the evolution constructor with the highest version number whose parameters are all assignable from the
    // provided property types.
    private fun findEvolverConstructor(constructors: List<EvolverConstructorInformation>,
                                       properties: Map<String, RemotePropertyInformation>): EvolverConstructorInformation? {
        val propertyTypes = properties.mapValues { (_, info) -> info.type.typeIdentifier.getLocalType(classLoader).asClass() }

        // Evolver constructors are listed in ascending version order, so we just want the last that matches.
        return constructors.lastOrNull { (_, evolverProperties) ->
            // We have a match if all mandatory evolver properties have a type-compatible property in the remote type.
            evolverProperties.all { (name, evolverProperty) ->
                val propertyType = propertyTypes[name]
                if (propertyType == null) !evolverProperty.isMandatory
                else evolverProperty.type.observedType.asClass().isAssignableFrom(propertyType)
            }
        }
    }

    private fun RemoteTypeInformation.Composable.validateEvolvability(
                                   localProperties: Map<PropertyName, LocalPropertyInformation>) {
        val remotePropertyNames = properties.keys
        val localPropertyNames = localProperties.keys
        val deletedProperties = remotePropertyNames - localPropertyNames
        val newProperties = localPropertyNames - remotePropertyNames

        // Here is where we can exercise a veto on evolutions that remove properties.
        if (deletedProperties.isNotEmpty() && mustPreserveData)
            throw EvolutionSerializationException(this,
                    "Property ${deletedProperties.first()} of remote type is not present in local type")

        // Check mandatory-ness of constructor-set properties.
        newProperties.forEach { propertyName ->
            if (localProperties[propertyName]!!.mustBeProvided) throw EvolutionSerializationException(
                    this,
                    "Mandatory property $propertyName of local type is not present in remote type")
        }
    }

    private val LocalPropertyInformation.mustBeProvided: Boolean get() = when(this) {
        is LocalPropertyInformation.ConstructorPairedProperty -> isMandatory
        is LocalPropertyInformation.PrivateConstructorPairedProperty -> isMandatory
        else -> false
    }

    private fun classifyEnum(
            remoteTypeInformation: RemoteTypeInformation.AnEnum,
            localTypeInformation: LocalTypeInformation.AnEnum): AMQPSerializer<Any> {
        if (remoteTypeInformation.members == localTypeInformation.members) return localSerializerFactory.get(localTypeInformation)
        val remoteTransforms = remoteTypeInformation.transforms
        val localTransforms = localTypeInformation.getEnumTransforms(localSerializerFactory)
        val transforms = if (remoteTransforms.size > localTransforms.size) remoteTransforms else localTransforms

        val localOrdinals = localTypeInformation.members.asSequence().mapIndexed { ord, member -> member to ord }.toMap()
        val remoteOrdinals = remoteTypeInformation.members.asSequence().mapIndexed { ord, member -> member to ord }.toMap()
        val rules = transforms.defaults + transforms.renames

        // We just trust our transformation rules not to contain cycles here.
        tailrec fun findLocal(remote: String): String =
            if (remote in localOrdinals) remote
            else findLocal(rules[remote] ?: throw EvolutionSerializationException(
                    remoteTypeInformation,
                    "Cannot resolve local enum member $remote to a member of ${localOrdinals.keys} using rules $rules"
            ))

        val conversions = remoteTypeInformation.members.associate { it to findLocal(it) }
        val convertedOrdinals = remoteOrdinals.asSequence().map { (member, ord) -> ord to conversions[member]!! }.toMap()
        if (localOrdinals.any { (name, ordinal) -> convertedOrdinals[ordinal] != name })
            throw EvolutionSerializationException(
                    remoteTypeInformation,
                    "Constants have been reordered, additions must be appended to the end")

        return EnumEvolutionSerializer(localTypeInformation.observedType, localSerializerFactory, conversions, localOrdinals)
    }

    private fun buildComposableEvolutionSerializer(
            remoteTypeInformation: RemoteTypeInformation.Composable,
            localTypeInformation: LocalTypeInformation.Composable,
            constructor: LocalConstructorInformation,
            properties: Map<String, LocalPropertyInformation>): AMQPSerializer<Any> =
        descriptorBasedSerializerRegistry.getOrBuild(remoteTypeInformation.typeDescriptor) {
            EvolutionObjectSerializer.make(
                    localTypeInformation,
                    remoteTypeInformation,
                    constructor,
                    properties,
                    classLoader)
        }
}