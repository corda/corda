package net.corda.serialization.internal.amqp

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.serialization.internal.model.*

@KeepForDJVM
object SerializerFactoryBuilder {

    @JvmStatic
    // Has to be named differently, otherwise serialization-deterministic:determinise fails.
    fun buildDeterministic(whitelist: ClassWhitelist, classCarpenter: ClassCarpenter) =
            makeFactory(
                    whitelist,
                    classCarpenter,
                    DefaultEvolutionSerializerProvider,
                    DefaultDescriptorBasedSerializerRegistry(),
                    null,
                    false)

    @JvmStatic
    @DeleteForDJVM
    @JvmOverloads
    fun build(
            whitelist: ClassWhitelist,
            classCarpenter: ClassCarpenter,
            evolutionSerializerProvider: EvolutionSerializerProvider = DefaultEvolutionSerializerProvider,
            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                    DefaultDescriptorBasedSerializerRegistry(),
            overrideFingerPrinter: FingerPrinter? = null,
            onlyCustomSerializers: Boolean = false) =
            makeFactory(
                    whitelist,
                    classCarpenter,
                    evolutionSerializerProvider,
                    descriptorBasedSerializerRegistry,
                    overrideFingerPrinter,
                    onlyCustomSerializers)

    @JvmStatic
    @DeleteForDJVM
    @JvmOverloads
    fun build(
            whitelist: ClassWhitelist,
            carpenterClassLoader: ClassLoader,
            lenientCarpenterEnabled: Boolean = false,
            evolutionSerializerProvider: EvolutionSerializerProvider = DefaultEvolutionSerializerProvider,
            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                    DefaultDescriptorBasedSerializerRegistry(),
            overrideFingerPrinter: FingerPrinter? = null,
            onlyCustomSerializers: Boolean = false) =
            makeFactory(
                    whitelist,
                    ClassCarpenterImpl(whitelist, carpenterClassLoader, lenientCarpenterEnabled),
                    evolutionSerializerProvider,
                    descriptorBasedSerializerRegistry,
                    overrideFingerPrinter,
                    onlyCustomSerializers)

    private fun makeFactory(whitelist: ClassWhitelist,
                            classCarpenter: ClassCarpenter,
                            evolutionSerializerProvider: EvolutionSerializerProvider,
                            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
                            overrideFingerPrinter: FingerPrinter?,
                            onlyCustomSerializers: Boolean): SerializerFactory {
        val customSerializerRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)

        val fingerPrinter = overrideFingerPrinter ?:
                getTypeModellingFingerPrinter(whitelist, customSerializerRegistry)

        val localSerializerFactory = DefaultLocalSerializerFactory(
                whitelist,
                fingerPrinter,
                classCarpenter.classloader,
                descriptorBasedSerializerRegistry,
                customSerializerRegistry,
                onlyCustomSerializers)

        val remoteSerializerFactory = DefaultRemoteSerializerFactory(
                classCarpenter,
                classCarpenter.classloader,
                evolutionSerializerProvider,
                descriptorBasedSerializerRegistry,
                localSerializerFactory)

        return ComposedSerializerFactory(localSerializerFactory, remoteSerializerFactory, customSerializerRegistry)
    }
}