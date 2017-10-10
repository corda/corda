@file:JvmName("AMQPSerializationScheme")

package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0
import net.corda.nodeapi.internal.serialization.amqp.DeserializationInput
import net.corda.nodeapi.internal.serialization.amqp.SerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val AMQP_ENABLED get() = SerializationDefaults.P2P_CONTEXT.preferredSerializationVersion == AmqpHeaderV1_0

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

abstract class AbstractAMQPSerializationScheme : SerializationScheme {
    internal companion object {
        private val serializationWhitelists: List<SerializationWhitelist> by lazy {
            ServiceLoader.load(SerializationWhitelist::class.java, this::class.java.classLoader).toList() + DefaultWhitelist
        }

        fun registerCustomSerializers(factory: SerializerFactory) {
            with(factory) {
                register(net.corda.nodeapi.internal.serialization.amqp.custom.PublicKeySerializer)
                register(net.corda.nodeapi.internal.serialization.amqp.custom.PrivateKeySerializer)
                register(net.corda.nodeapi.internal.serialization.amqp.custom.ThrowableSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.X500NameSerializer)
                register(net.corda.nodeapi.internal.serialization.amqp.custom.BigDecimalSerializer)
                register(net.corda.nodeapi.internal.serialization.amqp.custom.CurrencySerializer)
                register(net.corda.nodeapi.internal.serialization.amqp.custom.OpaqueBytesSubSequenceSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.InstantSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.DurationSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.LocalDateSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.LocalDateTimeSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.LocalTimeSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.ZonedDateTimeSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.ZoneIdSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.OffsetTimeSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.OffsetDateTimeSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.YearSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.YearMonthSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.MonthDaySerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.PeriodSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.ClassSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.X509CertificateHolderSerializer)
                register(net.corda.nodeapi.internal.serialization.amqp.custom.PartyAndCertificateSerializer(factory))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.StringBufferSerializer)
                register(net.corda.nodeapi.internal.serialization.amqp.custom.SimpleStringSerializer)
                register(net.corda.nodeapi.internal.serialization.amqp.custom.InputStreamSerializer)
                register(net.corda.nodeapi.internal.serialization.amqp.custom.BitSetSerializer(this))
                register(net.corda.nodeapi.internal.serialization.amqp.custom.EnumSetSerializer(this))
            }
            for (whitelistProvider in serializationWhitelists)
                factory.addToWhitelist(*whitelistProvider.whitelist.toTypedArray())
        }
    }

    private val serializerFactoriesForContexts = ConcurrentHashMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>()

    protected abstract fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory
    protected abstract fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory

    private fun getSerializerFactory(context: SerializationContext): SerializerFactory {
        return serializerFactoriesForContexts.computeIfAbsent(Pair(context.whitelist, context.deserializationClassLoader)) {
            when (context.useCase) {
                SerializationContext.UseCase.Checkpoint ->
                    throw IllegalStateException("AMQP should not be used for checkpoint serialization.")
                SerializationContext.UseCase.RPCClient ->
                    rpcClientSerializerFactory(context)
                SerializationContext.UseCase.RPCServer ->
                    rpcServerSerializerFactory(context)
                else -> SerializerFactory(context.whitelist, context.deserializationClassLoader)
            }
        }.also { registerCustomSerializers(it) }
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        val serializerFactory = getSerializerFactory(context)
        return DeserializationInput(serializerFactory).deserialize(byteSequence, clazz)
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        val serializerFactory = getSerializerFactory(context)
        return SerializationOutput(serializerFactory).serialize(obj)
    }

    protected fun canDeserializeVersion(byteSequence: ByteSequence): Boolean = AMQP_ENABLED && byteSequence == AmqpHeaderV1_0
}

// TODO: This will eventually cover server RPC as well and move to node module, but for now this is not implemented
class AMQPServerSerializationScheme : AbstractAMQPSerializationScheme() {
    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return (canDeserializeVersion(byteSequence) &&
                (target == SerializationContext.UseCase.P2P || target == SerializationContext.UseCase.Storage))
    }

}

// TODO: This will eventually cover client RPC as well and move to client module, but for now this is not implemented
class AMQPClientSerializationScheme : AbstractAMQPSerializationScheme() {
    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }

    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return (canDeserializeVersion(byteSequence) &&
                (target == SerializationContext.UseCase.P2P || target == SerializationContext.UseCase.Storage))
    }

}

val AMQP_P2P_CONTEXT = SerializationContextImpl(AmqpHeaderV1_0,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.P2P)
