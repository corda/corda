package net.corda.serialization.internal.amqp

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.serialization.internal.model.*
import java.io.NotSerializableException

@KeepForDJVM
object SerializerFactoryBuilder {

    @JvmStatic
    fun build(whitelist: ClassWhitelist, classCarpenter: ClassCarpenter): SerializerFactory {
        return makeFactory(
                whitelist,
                classCarpenter,
                DefaultDescriptorBasedSerializerRegistry(),
                allowEvolution = true,
                overrideFingerPrinter = null,
                onlyCustomSerializers = false,
                mustPreserveDataWhenEvolving = false,
                mustCarpentMissingTypes = false)
    }

    @JvmStatic
    @DeleteForDJVM
    fun build(
            whitelist: ClassWhitelist,
            classCarpenter: ClassCarpenter,
            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                    DefaultDescriptorBasedSerializerRegistry(),
            allowEvolution: Boolean = true,
            overrideFingerPrinter: FingerPrinter? = null,
            onlyCustomSerializers: Boolean = false,
            mustPreserveDataWhenEvolving: Boolean = false,
            // Set by environment variable DISABLE-CORDA-2707
            mustCarpentMissingTypes: Boolean = false): SerializerFactory {
        return makeFactory(
                whitelist,
                classCarpenter,
                descriptorBasedSerializerRegistry,
                allowEvolution,
                overrideFingerPrinter,
                onlyCustomSerializers,
                mustPreserveDataWhenEvolving,
                mustCarpentMissingTypes)
    }

    @JvmStatic
    @DeleteForDJVM
    fun build(
            whitelist: ClassWhitelist,
            carpenterClassLoader: ClassLoader,
            lenientCarpenterEnabled: Boolean = false,
            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                    DefaultDescriptorBasedSerializerRegistry(),
            allowEvolution: Boolean = true,
            overrideFingerPrinter: FingerPrinter? = null,
            onlyCustomSerializers: Boolean = false,
            mustPreserveDataWhenEvolving: Boolean = false,
            mustCarpentMissingTypes: Boolean = false): SerializerFactory {
        return makeFactory(
                whitelist,
                ClassCarpenterImpl(whitelist, carpenterClassLoader, lenientCarpenterEnabled),
                descriptorBasedSerializerRegistry,
                allowEvolution,
                overrideFingerPrinter,
                onlyCustomSerializers,
                mustPreserveDataWhenEvolving,
                mustCarpentMissingTypes)
    }

    private fun makeFactory(whitelist: ClassWhitelist,
                            classCarpenter: ClassCarpenter,
                            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
                            allowEvolution: Boolean,
                            overrideFingerPrinter: FingerPrinter?,
                            onlyCustomSerializers: Boolean,
                            mustPreserveDataWhenEvolving: Boolean,
                            mustCarpentMissingTypes: Boolean): SerializerFactory {
        val customSerializerRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)

        val localTypeModel = ConfigurableLocalTypeModel(
                WhitelistBasedTypeModelConfiguration(
                        whitelist,
                        customSerializerRegistry))

        val fingerPrinter = overrideFingerPrinter ?:
            TypeModellingFingerPrinter(customSerializerRegistry)

        val localSerializerFactory = DefaultLocalSerializerFactory(
                whitelist,
                localTypeModel,
                fingerPrinter,
                classCarpenter.classloader,
                descriptorBasedSerializerRegistry,
                customSerializerRegistry,
                onlyCustomSerializers)

        val typeLoader = ClassCarpentingTypeLoader(
                SchemaBuildingRemoteTypeCarpenter(classCarpenter),
                classCarpenter.classloader,
                mustCarpentMissingTypes)

        val evolutionSerializerFactory = if (allowEvolution) DefaultEvolutionSerializerFactory(
                localSerializerFactory,
                classCarpenter.classloader,
                mustPreserveDataWhenEvolving
        ) else NoEvolutionSerializerFactory

        val remoteSerializerFactory = DefaultRemoteSerializerFactory(
                evolutionSerializerFactory,
                descriptorBasedSerializerRegistry,
                AMQPRemoteTypeModel(),
                localTypeModel,
                typeLoader,
                localSerializerFactory)

        return ComposedSerializerFactory(localSerializerFactory, remoteSerializerFactory, customSerializerRegistry)
    }

}

object NoEvolutionSerializerFactory : EvolutionSerializerFactory {
    override fun getEvolutionSerializer(remoteTypeInformation: RemoteTypeInformation, localTypeInformation: LocalTypeInformation): AMQPSerializer<Any> {
        throw NotSerializableException("""
Evolution not permitted.

Remote:
${remoteTypeInformation.prettyPrint(false)}

Local:
${localTypeInformation.prettyPrint(false)}
        """)
    }
}