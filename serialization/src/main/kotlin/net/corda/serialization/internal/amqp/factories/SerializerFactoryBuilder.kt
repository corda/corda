package net.corda.serialization.internal.amqp.factories

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.serialization.internal.model.*
import net.corda.core.internal.reflection.LocalTypeInformation
import net.corda.core.internal.reflection.LocalTypeModel
import net.corda.serialization.internal.amqp.api.*
import net.corda.serialization.internal.amqp.schema.AMQPRemoteTypeModel
import net.corda.serialization.internal.carpenter.SchemaBuildingRemoteTypeCarpenter
import java.io.NotSerializableException

@KeepForDJVM
object SerializerFactoryBuilder {

    @JvmStatic
    fun build(whitelist: ClassWhitelist, classCarpenter: ClassCarpenter): SerializerFactory {
        return makeFactory(
                whitelist,
                classCarpenter,
                DefaultDescriptorBasedSerializerRegistry(),
                true,
                null,
                false,
                false)
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
            mustPreserveDataWhenEvolving: Boolean = false): SerializerFactory {
        return makeFactory(
                whitelist,
                classCarpenter,
                descriptorBasedSerializerRegistry,
                allowEvolution,
                overrideFingerPrinter,
                onlyCustomSerializers,
                mustPreserveDataWhenEvolving)
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
            mustPreserveDataWhenEvolving: Boolean = false): SerializerFactory {
        return makeFactory(
                whitelist,
                ClassCarpenterImpl(whitelist, carpenterClassLoader, lenientCarpenterEnabled),
                descriptorBasedSerializerRegistry,
                allowEvolution,
                overrideFingerPrinter,
                onlyCustomSerializers,
                mustPreserveDataWhenEvolving)
    }

    private fun makeFactory(whitelist: ClassWhitelist,
                            classCarpenter: ClassCarpenter,
                            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
                            allowEvolution: Boolean,
                            overrideFingerPrinter: FingerPrinter?,
                            onlyCustomSerializers: Boolean,
                            mustPreserveDataWhenEvolving: Boolean): SerializerFactory {
        val customSerializerRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)

        val localTypeModel = LocalTypeModel.configuredWith(
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
                classCarpenter.classloader)

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

private class ComposedSerializerFactory(
        private val localSerializerFactory: LocalSerializerFactory,
        private val remoteSerializerFactory: RemoteSerializerFactory,
        private val customSerializerRegistry: CachingCustomSerializerRegistry
) : SerializerFactory,
        LocalSerializerFactory by localSerializerFactory,
        RemoteSerializerFactory by remoteSerializerFactory,
        CustomSerializerRegistry by customSerializerRegistry