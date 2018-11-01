package net.corda.serialization.internal.amqp

import com.google.common.primitives.Primitives
import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.serialization.internal.model.*
import org.apache.qpid.proton.amqp.*
import java.lang.reflect.Type
import java.util.*

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

        val localTypeModel = ConfigurableLocalTypeModel(
                WhitelistBasedTypeModelConfiguration(
                        whitelist,
                        customSerializerRegistry))

        val fingerPrinter = overrideFingerPrinter ?:
            CustomisableLocalTypeInformationFingerPrinter(customSerializerRegistry, localTypeModel)

        val fingerprintingLocalTypeModel = DefaultFingerprintingLocalTypeModel(localTypeModel, fingerPrinter)

        val localSerializerFactory = DefaultLocalSerializerFactory(
                whitelist,
                fingerprintingLocalTypeModel,
                classCarpenter.classloader,
                descriptorBasedSerializerRegistry,
                customSerializerRegistry,
                onlyCustomSerializers)

        val typeLoader = ClassCarpentingTypeLoader(classCarpenter, classCarpenter.classloader)

        val remoteTypeReflector = TypeLoadingRemoteTypeReflector(
                typeLoader,
                fingerprintingLocalTypeModel)

        val remoteSerializerFactory = DefaultRemoteSerializerFactory(
                classCarpenter,
                classCarpenter.classloader,
                evolutionSerializerProvider,
                descriptorBasedSerializerRegistry,
                AMQPRemoteTypeModel(),
                remoteTypeReflector,
                localSerializerFactory)

        return ComposedSerializerFactory(localSerializerFactory, remoteSerializerFactory, customSerializerRegistry)
    }

}

/**
 * [LocalTypeModelConfiguration] based on a [ClassWhitelist]
 */
class WhitelistBasedTypeModelConfiguration(
        private val whitelist: ClassWhitelist,
        private val customSerializerRegistry: CustomSerializerRegistry)
    : LocalTypeModelConfiguration {
    override fun isExcluded(type: Type): Boolean = whitelist.isNotWhitelisted(type.asClass())
    override fun isOpaque(type: Type): Boolean = Primitives.unwrap(type.asClass()) in opaqueTypes ||
            customSerializerRegistry.findCustomSerializer(type.asClass(), type) != null
}

// Copied from SerializerFactory so that we can have equivalent behaviour, for now.
private val opaqueTypes = setOf(
        Character::class.java,
        Char::class.java,
        Boolean::class.java,
        Byte::class.java,
        UnsignedByte::class.java,
        Short::class.java,
        UnsignedShort::class.java,
        Int::class.java,
        UnsignedInteger::class.java,
        Long::class.java,
        UnsignedLong::class.java,
        Float::class.java,
        Double::class.java,
        Decimal32::class.java,
        Decimal64::class.java,
        Decimal128::class.java,
        Date::class.java,
        UUID::class.java,
        ByteArray::class.java,
        String::class.java,
        Symbol::class.java
)