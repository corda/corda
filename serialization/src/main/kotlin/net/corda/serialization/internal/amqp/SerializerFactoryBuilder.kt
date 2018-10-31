package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.serialization.internal.model.*
import java.lang.reflect.Type

@KeepForDJVM
object SerializerFactoryBuilder {

    @JvmStatic
    // Has to be named differently, otherwise serialization-deterministic:determinise fails mysteriously.
    fun buildDeterministic(whitelist: ClassWhitelist, classCarpenter: ClassCarpenter) =
            makeFactory(
                    whitelist,
                    classCarpenter,
                    DefaultEvolutionSerializerProvider,
                    null,
                    false)

    @JvmStatic
    @JvmOverloads
    fun build(
            whitelist: ClassWhitelist,
            classCarpenter: ClassCarpenter,
            evolutionSerializerProvider: EvolutionSerializerProvider = DefaultEvolutionSerializerProvider,
            customFingerPrinter: FingerPrinter? = null,
            onlyCustomSerializers: Boolean = false) =
            makeFactory(
                    whitelist,
                    classCarpenter,
                    evolutionSerializerProvider,
                    customFingerPrinter,
                    onlyCustomSerializers)

    @JvmStatic
    @JvmOverloads
    fun build(
            whitelist: ClassWhitelist,
            carpenterClassLoader: ClassLoader,
            lenientCarpenterEnabled: Boolean = false,
            evolutionSerializerProvider: EvolutionSerializerProvider = DefaultEvolutionSerializerProvider,
            customFingerPrinter: FingerPrinter? = null,
            onlyCustomSerializers: Boolean = false) =
            makeFactory(
                    whitelist,
                    ClassCarpenterImpl(whitelist, carpenterClassLoader, lenientCarpenterEnabled),
                    evolutionSerializerProvider,
                    customFingerPrinter,
                    onlyCustomSerializers)

    private fun makeFactory(whitelist: ClassWhitelist,
                            classCarpenter: ClassCarpenter,
                            evolutionSerializerProvider: EvolutionSerializerProvider,
                            customFingerPrinter: FingerPrinter?,
                            onlyCustomSerializers: Boolean): SerializerFactory {
        val descriptorBasedSerializerRegistry = AMQPDescriptorBasedSerializerLookupRegistry()
        val customSerializerRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)

        val fingerPrinter = customFingerPrinter ?:
                getTypeModellingFingerPrinter(whitelist, customSerializerRegistry)

        val localSerializerFactory = DefaultLocalSerializerFactory(
                whitelist,
                fingerPrinter,
                classCarpenter.classloader,
                descriptorBasedSerializerRegistry,
                customSerializerRegistry,
                onlyCustomSerializers)

        val evolutionSerializerFactory = ComposedEvolutionSerializerFactory(
                localSerializerFactory,
                descriptorBasedSerializerRegistry
        )

        val remoteSerializerFactory = DefaultRemoteSerializerFactory(
                classCarpenter,
                classCarpenter.classloader,
                evolutionSerializerProvider,
                descriptorBasedSerializerRegistry,
                evolutionSerializerFactory)

        return ComposedSerializerFactory(localSerializerFactory, remoteSerializerFactory, customSerializerRegistry)
    }
}