@file:JvmName("AMQPSerializationScheme")

package net.corda.serialization.internal.amqp

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.StubOutForDJVM
import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.objectOrNewInstance
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.contextLogger
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.DefaultWhitelist
import net.corda.serialization.internal.MutableClassWhitelist
import net.corda.serialization.internal.SerializationScheme
import java.lang.reflect.Modifier
import java.util.*

val AMQP_ENABLED get() = SerializationDefaults.P2P_CONTEXT.preferredSerializationVersion == amqpMagic

fun SerializerFactory.addToWhitelist(vararg types: Class<*>) {
    require(types.toSet().size == types.size) {
        val duplicates = types.toMutableList()
        types.toSet().forEach { duplicates -= it }
        "Cannot add duplicate classes to the whitelist ($duplicates)."
    }
    for (type in types) {
        (this.whitelist as? MutableClassWhitelist)?.add(type)
    }
}

// Allow us to create a SerializerFactory with a different ClassCarpenter implementation.
interface SerializerFactoryFactory {
    fun make(context: SerializationContext): SerializerFactory
}

@KeepForDJVM
abstract class AbstractAMQPSerializationScheme(
        private val cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
        private val serializerFactoriesForContexts: AccessOrderLinkedHashMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>,
        val sff: SerializerFactoryFactory = createSerializerFactoryFactory()
) : SerializationScheme {
    @DeleteForDJVM
    constructor(cordapps: List<Cordapp>) : this(cordapps.customSerializers, AccessOrderLinkedHashMap(128))

    // TODO: This method of initialisation for the Whitelist and plugin serializers will have to change
    //       when we have per-cordapp contexts and dynamic app reloading but for now it's the easiest way
    companion object {
        const val SCAN_SPEC_PROP_NAME = "amqp.custom.serialization.scanSpec"

        private val logger = contextLogger()

        private val serializationWhitelists: List<SerializationWhitelist> by lazy {
            ServiceLoader.load(SerializationWhitelist::class.java, this::class.java.classLoader).toList() + DefaultWhitelist
        }

        private val customSerializers: List<SerializationCustomSerializer<*, *>> by lazy {
            val scanSpec: String? = System.getProperty(SCAN_SPEC_PROP_NAME)

            if (scanSpec == null) {
                logger.debug("scanSpec not set, not scanning for Custom Serializers")
                emptyList()
            } else {
                logger.debug("scanSpec = \"$scanSpec\", scanning for Custom Serializers")
                scanClasspathForSerializers(scanSpec)
            }
        }

        @StubOutForDJVM
        private fun scanClasspathForSerializers(scanSpec: String): List<SerializationCustomSerializer<*, *>> =
                this::class.java.classLoader.let { cl ->
                    FastClasspathScanner(scanSpec).addClassLoader(cl).scan()
                            .getNamesOfClassesImplementing(SerializationCustomSerializer::class.java)
                            .map { cl.loadClass(it).asSubclass(SerializationCustomSerializer::class.java) }
                            .filterNot { Modifier.isAbstract(it.modifiers) }
                            .map { it.kotlin.objectOrNewInstance() }
                }

        @DeleteForDJVM
        val List<Cordapp>.customSerializers
            get() = flatMap { it.serializationCustomSerializers }.toSet()
    }

    // Parameter "context" is unused directly but passed in by reflection. Removing it will cause failures.
    private fun registerCustomSerializers(context: SerializationContext, factory: SerializerFactory) {
        with(factory) {
            register(publicKeySerializer)
            register(net.corda.serialization.internal.amqp.custom.PrivateKeySerializer)
            register(net.corda.serialization.internal.amqp.custom.ThrowableSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.BigDecimalSerializer)
            register(net.corda.serialization.internal.amqp.custom.BigIntegerSerializer)
            register(net.corda.serialization.internal.amqp.custom.CurrencySerializer)
            register(net.corda.serialization.internal.amqp.custom.OpaqueBytesSubSequenceSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.InstantSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.DurationSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.LocalDateSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.LocalDateTimeSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.LocalTimeSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.ZonedDateTimeSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.ZoneIdSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.OffsetTimeSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.OffsetDateTimeSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.OptionalSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.YearSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.YearMonthSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.MonthDaySerializer(this))
            register(net.corda.serialization.internal.amqp.custom.PeriodSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.ClassSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.X509CertificateSerializer)
            register(net.corda.serialization.internal.amqp.custom.X509CRLSerializer)
            register(net.corda.serialization.internal.amqp.custom.CertPathSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.StringBufferSerializer)
            register(net.corda.serialization.internal.amqp.custom.InputStreamSerializer)
            register(net.corda.serialization.internal.amqp.custom.BitSetSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.EnumSetSerializer(this))
            register(net.corda.serialization.internal.amqp.custom.ContractAttachmentSerializer(this))
            registerNonDeterministicSerializers(factory)
        }
        for (whitelistProvider in serializationWhitelists) {
            factory.addToWhitelist(*whitelistProvider.whitelist.toTypedArray())
        }

        // If we're passed in an external list we trust that, otherwise revert to looking at the scan of the
        // classpath to find custom serializers.
        if (cordappCustomSerializers.isEmpty()) {
            for (customSerializer in customSerializers) {
                factory.registerExternal(CorDappCustomSerializer(customSerializer, factory))
            }
        } else {
            logger.debug("Custom Serializer list loaded - not scanning classpath")
            cordappCustomSerializers.forEach { customSerializer ->
                factory.registerExternal(CorDappCustomSerializer(customSerializer, factory))
            }
        }

        context.properties[ContextPropertyKeys.SERIALIZERS]?.apply {
            uncheckedCast<Any, List<CustomSerializer<out Any>>>(this).forEach {
                factory.register(it)
            }
        }
    }

    /*
     * Register the serializers which will be excluded from the DJVM.
     */
    @StubOutForDJVM
    private fun registerNonDeterministicSerializers(factory: SerializerFactory) {
        with(factory) {
            register(net.corda.serialization.internal.amqp.custom.SimpleStringSerializer)
        }
    }

    protected abstract fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory
    protected abstract fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory

    // Not used as a simple direct import to facilitate testing
    open val publicKeySerializer: CustomSerializer<*> = net.corda.serialization.internal.amqp.custom.PublicKeySerializer

    private fun getSerializerFactory(context: SerializationContext): SerializerFactory {
        return synchronized(serializerFactoriesForContexts) {
            serializerFactoriesForContexts.computeIfAbsent(Pair(context.whitelist, context.deserializationClassLoader)) {
                when (context.useCase) {
                    SerializationContext.UseCase.Checkpoint ->
                        throw IllegalStateException("AMQP should not be used for checkpoint serialization.")
                    SerializationContext.UseCase.RPCClient ->
                        rpcClientSerializerFactory(context)
                    SerializationContext.UseCase.RPCServer ->
                        rpcServerSerializerFactory(context)
                    else -> sff.make(context)
                }.also {
                    registerCustomSerializers(context, it)
                }
            }
        }
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        var contextToUse = context
        if (context.useCase == SerializationContext.UseCase.RPCClient) {
            contextToUse = context.withClassLoader(getContextClassLoader())
        }
        val serializerFactory = getSerializerFactory(contextToUse)
        return DeserializationInput(serializerFactory).deserialize(byteSequence, clazz, context)
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        var contextToUse = context
        if (context.useCase == SerializationContext.UseCase.RPCClient) {
            contextToUse = context.withClassLoader(getContextClassLoader())
        }
        val serializerFactory = getSerializerFactory(contextToUse)
        return SerializationOutput(serializerFactory).serialize(obj, context)
    }

    protected fun canDeserializeVersion(magic: CordaSerializationMagic) = magic == amqpMagic
}
