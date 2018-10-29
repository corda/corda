package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl

@KeepForDJVM
object SerializerFactoryBuilder {

    @JvmStatic
    fun buildWithCarpenter(whitelist: ClassWhitelist, classCarpenter: ClassCarpenter) =
            build(whitelist, classCarpenter, DefaultEvolutionSerializerProvider, ::SerializerFingerPrinter, false)

    @JvmStatic
    fun buildWithCarpenterClassloader(whitelist: ClassWhitelist,
                                      carpenterClassLoader: ClassLoader,
                                      lenientCarpenter: Boolean = false) =
            buildWithCarpenter(whitelist, ClassCarpenterImpl(whitelist, carpenterClassLoader, lenientCarpenter))

    @JvmStatic
    fun buildWithCustomEvolutionSerializerProvider(whitelist: ClassWhitelist,
                                                   classCarpenter: ClassCarpenter,
                                                   evolutionSerializerProvider: EvolutionSerializerProvider) =
            build(whitelist,
                    classCarpenter,
                    evolutionSerializerProvider,
                    ::SerializerFingerPrinter,
                    false)

    @JvmStatic
    fun buildWithCustomFingerprinter(whitelist: ClassWhitelist,
                                     classCarpenter: ClassCarpenter,
                                     evolutionSerializerProvider: EvolutionSerializerProvider,
                                     fingerPrinterProvider: (SerializerFactory) -> FingerPrinter) =
            build(whitelist,
                    classCarpenter,
                    evolutionSerializerProvider,
                    fingerPrinterProvider,
                    false)

    @JvmStatic
    fun buildOnlyCustomerSerializers(whitelist: ClassWhitelist,
                                     classCarpenter: ClassCarpenter,
                                     evolutionSerializerProvider: EvolutionSerializerProvider) =
            build(whitelist,
                    classCarpenter,
                    evolutionSerializerProvider,
                    ::SerializerFingerPrinter,
                    true)

    @JvmStatic
    fun build(whitelist: ClassWhitelist,
              classCarpenter: ClassCarpenter,
              evolutionSerializerProvider: EvolutionSerializerProvider,
              fingerPrinterProvider: (SerializerFactory) -> FingerPrinter,
              onlyCustomSerializers: Boolean) =
            SerializerFactory(whitelist, classCarpenter, evolutionSerializerProvider, fingerPrinterProvider, onlyCustomSerializers)

}