@file:Suppress("platform_class_mapped_to_kotlin")
package net.corda.serialization.djvm

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.AMQPRemoteTypeModel
import net.corda.serialization.internal.amqp.AMQPSerializer
import net.corda.serialization.internal.amqp.CachingCustomSerializerRegistry
import net.corda.serialization.internal.amqp.ComposedSerializerFactory
import net.corda.serialization.internal.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.serialization.internal.amqp.DefaultEvolutionSerializerFactory
import net.corda.serialization.internal.amqp.DefaultLocalSerializerFactory
import net.corda.serialization.internal.amqp.DefaultRemoteSerializerFactory
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.SerializerFactoryFactory
import net.corda.serialization.internal.amqp.WhitelistBasedTypeModelConfiguration
import net.corda.serialization.internal.amqp.createClassCarpenter
import net.corda.serialization.internal.model.BaseLocalTypes
import net.corda.serialization.internal.model.ClassCarpentingTypeLoader
import net.corda.serialization.internal.model.ConfigurableLocalTypeModel
import net.corda.serialization.internal.model.SchemaBuildingRemoteTypeCarpenter
import net.corda.serialization.internal.amqp.SerializerFactoryBuilder
import net.corda.serialization.internal.model.TypeLoader
import net.corda.serialization.internal.model.TypeModellingFingerPrinter
import java.lang.Boolean
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Long
import java.lang.Short
import java.util.Collections.singleton
import java.util.Collections.unmodifiableMap
import java.util.Date
import java.util.UUID
import java.util.function.Function
import java.util.function.Predicate

/**
 * This has all been lovingly copied from [SerializerFactoryBuilder].
 */
class SandboxSerializerFactoryFactory(
    private val primitiveSerializerFactory: Function<Class<*>, AMQPSerializer<Any>>,
    private val localTypes: BaseLocalTypes
) : SerializerFactoryFactory {

    override fun make(context: SerializationContext): SerializerFactory {
        val classLoader = context.deserializationClassLoader

        val primitiveTypes = unmodifiableMap(mapOf<Class<*>, Class<*>>(
            classLoader.loadClass("sandbox.java.lang.Boolean") to Boolean.TYPE,
            classLoader.loadClass("sandbox.java.lang.Byte") to Byte.TYPE,
            classLoader.loadClass("sandbox.java.lang.Character") to Character.TYPE,
            classLoader.loadClass("sandbox.java.lang.Double") to Double.TYPE,
            classLoader.loadClass("sandbox.java.lang.Float") to Float.TYPE,
            classLoader.loadClass("sandbox.java.lang.Integer") to Integer.TYPE,
            classLoader.loadClass("sandbox.java.lang.Long") to Long.TYPE,
            classLoader.loadClass("sandbox.java.lang.Short") to Short.TYPE,
            classLoader.loadClass("sandbox.java.lang.String") to String::class.java,
            classLoader.loadClass("sandbox.java.util.Date") to Date::class.java,
            classLoader.loadClass("sandbox.java.util.UUID") to UUID::class.java,
            Void::class.java to Void.TYPE
        ))

        val classCarpenter = createClassCarpenter(context)
        val descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry()
        val customSerializerRegistry = CachingCustomSerializerRegistry(
            descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
            allowedFor = singleton(classLoader.loadClass("sandbox.java.lang.Object"))
        )

        val localTypeModel = ConfigurableLocalTypeModel(
            WhitelistBasedTypeModelConfiguration(
                whitelist = context.whitelist,
                customSerializerRegistry = customSerializerRegistry,
                baseTypes = localTypes
            )
        )

        val fingerPrinter = TypeModellingFingerPrinter(customSerializerRegistry, classLoader)

        val localSerializerFactory = DefaultLocalSerializerFactory(
            whitelist = context.whitelist,
            typeModel = localTypeModel,
            fingerPrinter = fingerPrinter,
            classloader = classLoader,
            descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
            primitiveSerializerFactory = primitiveSerializerFactory,
            isPrimitiveType = Predicate { clazz -> clazz.isPrimitive || clazz in primitiveTypes.keys },
            customSerializerRegistry = customSerializerRegistry,
            onlyCustomSerializers = false
        )

        val typeLoader: TypeLoader = ClassCarpentingTypeLoader(
            carpenter = SchemaBuildingRemoteTypeCarpenter(classCarpenter),
            classLoader = classLoader
        )

        val evolutionSerializerFactory = DefaultEvolutionSerializerFactory(
            localSerializerFactory = localSerializerFactory,
            classLoader = classLoader,
            mustPreserveDataWhenEvolving = context.preventDataLoss,
            primitiveTypes = primitiveTypes
        )

        val remoteSerializerFactory = DefaultRemoteSerializerFactory(
            evolutionSerializerFactory = evolutionSerializerFactory,
            descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
            remoteTypeModel = AMQPRemoteTypeModel(),
            localTypeModel = localTypeModel,
            typeLoader = typeLoader,
            localSerializerFactory = localSerializerFactory
        )

        return ComposedSerializerFactory(localSerializerFactory, remoteSerializerFactory, customSerializerRegistry)
    }
}
