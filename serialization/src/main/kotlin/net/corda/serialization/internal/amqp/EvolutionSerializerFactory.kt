package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.*
import java.io.NotSerializableException

interface EvolutionSerializerFactory {
    fun getEvolutionSerializer(
            remoteTypeInformation: RemoteTypeInformation,
            localTypeInformation: LocalTypeInformation): AMQPSerializer<Any>
}

class EvolutionSerializationException(remoteTypeInformation: RemoteTypeInformation, path: List<String>, reason: String)
    : NotSerializableException(
        """
            Cannot construct evolution serializer for remote type $remoteTypeInformation.

            $path: $reason
        """.trimIndent()
)

class DefaultEvolutionSerializerFactory(
        private val localSerializerFactory: LocalSerializerFactory,
        private val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
        private val reflector: RemoteTypeReflector,
        private val classLoader: ClassLoader,
        private val mustPreserveData: Boolean): EvolutionSerializerFactory {

    override fun getEvolutionSerializer(remoteTypeInformation: RemoteTypeInformation,
                                        localTypeInformation: LocalTypeInformation): AMQPSerializer<Any> {
        val classified = getClassifiedSerializer(remoteTypeInformation, localTypeInformation, emptyList())
        return classified.serializer
    }
    
    private fun getClassifiedSerializer(
            remoteTypeInformation: RemoteTypeInformation,
            localTypeInformation: LocalTypeInformation,
            path: List<String>): ClassifiedSerializer {
        val local = (localTypeInformation as? LocalTypeInformation.Cycle)?.follow ?: localTypeInformation

        val classified = when(remoteTypeInformation) {
            is RemoteTypeInformation.Composable -> when(local) {
                is LocalTypeInformation.Composable -> classifyComposable(remoteTypeInformation, local, path)
                else -> throw EvolutionSerializationException(remoteTypeInformation, path, "Local type ${local.typeIdentifier.prettyPrint(false)} is not composable")
            }
            is RemoteTypeInformation.AnEnum -> when(local) {
                is LocalTypeInformation.AnEnum -> classifyEnum(remoteTypeInformation, local, path)
                else -> throw EvolutionSerializationException(remoteTypeInformation, path, "Local type ${local.typeIdentifier.prettyPrint(false)} is not an enum")
            }
            is RemoteTypeInformation.AnInterface -> when(local) {
                is LocalTypeInformation.AnInterface -> compatible(local)
                else -> throw EvolutionSerializationException(remoteTypeInformation, path, "Local type ${local.typeIdentifier.prettyPrint(false)} is not an interface")
            }
            is RemoteTypeInformation.Parameterised -> compatible(local)
            is RemoteTypeInformation.Unparameterised -> when(local) {
                is LocalTypeInformation.Atomic,
                is LocalTypeInformation.Opaque -> compatible(local)
                else -> throw EvolutionSerializationException(remoteTypeInformation, path, "Local type ${local.typeIdentifier.prettyPrint(false)} is not unparameterised")
            }
            is RemoteTypeInformation.AnArray -> when(local) {
                is LocalTypeInformation.AnArray -> getClassifiedSerializer(remoteTypeInformation.componentType, local.componentType, path)
                else -> throw EvolutionSerializationException(remoteTypeInformation, path, "Local type ${local.typeIdentifier.prettyPrint(false)} is not an array")
            }
            is RemoteTypeInformation.Cycle ->
                throw EvolutionSerializationException(remoteTypeInformation, path, "Remote type contains reference cycles")
            is RemoteTypeInformation.Top,
            is RemoteTypeInformation.Unknown -> ClassifiedSerializer.CompatibleNoSerializer
        }

        if (classified !is ClassifiedSerializer.CompatibleNoSerializer) {
            descriptorBasedSerializerRegistry[remoteTypeInformation.typeDescriptor] = classified.serializer
        }

        return classified
    }
    
    private fun compatible(localTypeInformation: LocalTypeInformation) =
            ClassifiedSerializer.Compatible(localSerializerFactory.get(localTypeInformation))
    
    private fun classifyComposable(
            remoteTypeInformation: RemoteTypeInformation.Composable,
            localTypeInformation: LocalTypeInformation.Composable,
            path: List<String>): ClassifiedSerializer {
        val remoteProperties = remoteTypeInformation.properties
        val evolverConstructor = findEvolverConstructor(localTypeInformation.evolverConstructors, remoteProperties)
        val localProperties = evolverConstructor?.properties ?: localTypeInformation.properties

        val classifiedPropertySerializers = classifyProperties(remoteTypeInformation, remoteProperties, localProperties, path)

        // The no-op case: all properties match compatibly, we don't need no evolution.
        if (classifiedPropertySerializers.values.all { it is ClassifiedSerializer.Compatible } &&
                remoteTypeInformation.properties.size == localTypeInformation.properties.size &&
                classifiedPropertySerializers.size == localProperties.size ) {
            return compatible(localTypeInformation)
        }

        // We have to create an evolution serializer.
        // At this point, evolution serializers for all property types that need to evolve should have been built
        // and registered.
        return ClassifiedSerializer.Evolvable(
                buildComposableEvolutionSerializer(
                        remoteTypeInformation,
                        localTypeInformation,
                        evolverConstructor?.constructor ?: localTypeInformation.constructor,
                        localProperties))
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

    private fun classifyProperties(remoteTypeInformation: RemoteTypeInformation,
                                   remoteProperties: Map<String, RemotePropertyInformation>,
                                   localProperties: Map<PropertyName, LocalPropertyInformation>,
                                   path: List<String>): Map<PropertyName, ClassifiedSerializer> {
        val remotePropertyNames = remoteProperties.keys
        val localPropertyNames = localProperties.keys
        val deletedProperties = remotePropertyNames - localPropertyNames
        val newProperties = localPropertyNames - remotePropertyNames

        if (deletedProperties.isNotEmpty() && mustPreserveData)
            throw EvolutionSerializationException(remoteTypeInformation, path,
                    "Property ${deletedProperties.first()} of remote type is not present in local type")

        newProperties.forEach { propertyName ->
            val localProperty = localProperties[propertyName]!!
            if ((localProperty is LocalPropertyInformation.PrivateConstructorPairedProperty ||
                            localProperty is LocalPropertyInformation.PrivateConstructorPairedProperty) &&
                    localProperty.isMandatory) throw EvolutionSerializationException(
                    remoteTypeInformation,
                    path + propertyName,
                    "Mandatory property $propertyName of local type is not present in remote type")
        }

        val classifiedPropertySerializers = remotePropertyNames.associate { propertyName ->
            val remoteProperty = remoteProperties[propertyName]!!
            val localProperty = localProperties[propertyName]
            val serializer = if (localProperty == null) {
                getClassifiedSerializer(remoteProperty.type, reflector.reflect(remoteProperty.type), path + propertyName)
            } else {
                classifyProperty(
                        remoteTypeInformation,
                        propertyName,
                        remoteProperty,
                        localProperty,
                        path)
            }
            propertyName to serializer
        }

        return classifiedPropertySerializers
    }

    private fun classifyProperty(remoteTypeInformation: RemoteTypeInformation,
                                 propertyName: String,
                                 remoteProperty: RemotePropertyInformation,
                                 localProperty: LocalPropertyInformation,
                                 path: List<String>): ClassifiedSerializer {

        if (localProperty.isMandatory && !remoteProperty.isMandatory) {
            throw EvolutionSerializationException(remoteTypeInformation, path + propertyName,
                    "Mandatory property $propertyName of local type is not mandatory in remote type")
        }
        val remotePropertyType = remoteProperty.type
        val localPropertyType = localProperty.type

        val remotePropertyClass = remotePropertyType.typeIdentifier.getLocalType(classLoader).asClass()
        val localPropertyClass = localPropertyType.typeIdentifier.getLocalType(classLoader).asClass()
        if (!localPropertyClass.isAssignableFrom(remotePropertyClass)) {
                    throw EvolutionSerializationException(remoteTypeInformation, path + propertyName,
                            "Type $remotePropertyClass of property $propertyName is not assignable to $localPropertyClass")
                }

        return getClassifiedSerializer(remotePropertyType, localPropertyType, path + propertyName)
    }

    private fun classifyEnum(
            remoteTypeInformation: RemoteTypeInformation.AnEnum,
            localTypeInformation: LocalTypeInformation.AnEnum,
            path: List<String>): ClassifiedSerializer {
        throw UnsupportedOperationException()
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


sealed class ClassifiedSerializer {

    abstract val serializer: AMQPSerializer<Any>

    object CompatibleNoSerializer: ClassifiedSerializer() {
        override val serializer: AMQPSerializer<Any> get() =
                throw UnsupportedOperationException()
    }

    data class Compatible(override val serializer: AMQPSerializer<Any>): ClassifiedSerializer()
    data class Evolvable(override val serializer: AMQPSerializer<Any>): ClassifiedSerializer()
}