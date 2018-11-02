package net.corda.serialization.internal.amqp

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl

@KeepForDJVM
object SerializerFactoryBuilder {
    
    @JvmStatic
    @JvmOverloads
    fun build(
            whitelist: ClassWhitelist,
            classCarpenter: ClassCarpenter,
            evolutionSerializerProvider: EvolutionSerializerProvider = DefaultEvolutionSerializerProvider,
            fingerPrinterProvider: (SerializerFactory) -> FingerPrinter = ::SerializerFingerPrinter,
            onlyCustomSerializers: Boolean = false) =
            makeFactory(
                    whitelist,
                    classCarpenter,
                    evolutionSerializerProvider,
                    fingerPrinterProvider,
                    onlyCustomSerializers)

    @JvmStatic
    @JvmOverloads
    @DeleteForDJVM
    fun build(
            whitelist: ClassWhitelist,
            carpenterClassLoader: ClassLoader,
            lenientCarpenterEnabled: Boolean = false,
            evolutionSerializerProvider: EvolutionSerializerProvider = DefaultEvolutionSerializerProvider,
            fingerPrinterProvider: (SerializerFactory) -> FingerPrinter = ::SerializerFingerPrinter,
            onlyCustomSerializers: Boolean = false) =
            makeFactory(
                    whitelist,
                    ClassCarpenterImpl(whitelist, carpenterClassLoader, lenientCarpenterEnabled),
                    evolutionSerializerProvider,
                    fingerPrinterProvider,
                    onlyCustomSerializers)

    private fun makeFactory(whitelist: ClassWhitelist,
                            classCarpenter: ClassCarpenter,
                            evolutionSerializerProvider: EvolutionSerializerProvider,
                            fingerPrinterProvider: (SerializerFactory) -> FingerPrinter,
                            onlyCustomSerializers: Boolean) =
            DefaultSerializerFactory(whitelist, classCarpenter, evolutionSerializerProvider, fingerPrinterProvider,
                    onlyCustomSerializers)
}