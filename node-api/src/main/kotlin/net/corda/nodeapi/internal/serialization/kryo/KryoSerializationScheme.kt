package net.corda.nodeapi.internal.serialization.kryo

import java.util.concurrent.ConcurrentHashMap
import java.io.ByteArrayOutputStream
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ByteSequence
import net.corda.core.serialization.*
import net.corda.core.internal.LazyPool
import net.corda.nodeapi.internal.serialization.CordaClassResolver
import net.corda.nodeapi.internal.serialization.SerializationScheme
import java.security.PublicKey

// "corda" + majorVersionByte + minorVersionMSB + minorVersionLSB
val KryoHeaderV0_1: OpaqueBytes = OpaqueBytes("corda\u0000\u0000\u0001".toByteArray(Charsets.UTF_8))

private object AutoCloseableSerialisationDetector : Serializer<AutoCloseable>() {
    override fun write(kryo: Kryo, output: Output, closeable: AutoCloseable) {
        val message = "${closeable.javaClass.name}, which is a closeable resource, has been detected during flow checkpointing. " +
                "Restoring such resources across node restarts is not supported. Make sure code accessing it is " +
                "confined to a private method or the reference is nulled out."
        throw UnsupportedOperationException(message)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<AutoCloseable>) = throw IllegalStateException("Should not reach here!")
}

abstract class AbstractKryoSerializationScheme : SerializationScheme {
    private val kryoPoolsForContexts = ConcurrentHashMap<Pair<ClassWhitelist, ClassLoader>, KryoPool>()

    protected abstract fun rpcClientKryoPool(context: SerializationContext): KryoPool
    protected abstract fun rpcServerKryoPool(context: SerializationContext): KryoPool

    // this can be overriden in derived serialization schemes
    open protected val publicKeySerializer: Serializer<PublicKey> = PublicKeySerializer

    private fun getPool(context: SerializationContext): KryoPool {
        return kryoPoolsForContexts.computeIfAbsent(Pair(context.whitelist, context.deserializationClassLoader)) {
            when (context.useCase) {
                SerializationContext.UseCase.Checkpoint ->
                    KryoPool.Builder {
                        val serializer = Fiber.getFiberSerializer(false) as KryoSerializer
                        val classResolver = CordaClassResolver(context).apply { setKryo(serializer.kryo) }
                        // TODO The ClassResolver can only be set in the Kryo constructor and Quasar doesn't provide us with a way of doing that
                        val field = Kryo::class.java.getDeclaredField("classResolver").apply { isAccessible = true }
                        serializer.kryo.apply {
                            field.set(this, classResolver)
                            // don't allow overriding the public key serializer for checkpointing
                            DefaultKryoCustomizer.customize(this)
                            addDefaultSerializer(AutoCloseable::class.java, AutoCloseableSerialisationDetector)
                            register(ClosureSerializer.Closure::class.java, CordaClosureSerializer)
                            classLoader = it.second
                        }
                    }.build()
                SerializationContext.UseCase.RPCClient ->
                    rpcClientKryoPool(context)
                SerializationContext.UseCase.RPCServer ->
                    rpcServerKryoPool(context)
                else ->
                    KryoPool.Builder {
                        DefaultKryoCustomizer.customize(CordaKryo(CordaClassResolver(context)), publicKeySerializer).apply { classLoader = it.second }
                    }.build()
            }
        }
    }

    private fun <T : Any> withContext(kryo: Kryo, context: SerializationContext, block: (Kryo) -> T): T {
        kryo.context.ensureCapacity(context.properties.size)
        context.properties.forEach { kryo.context.put(it.key, it.value) }
        try {
            return block(kryo)
        } finally {
            kryo.context.clear()
        }
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        val pool = getPool(context)
        val headerSize = KryoHeaderV0_1.size
        val header = byteSequence.take(headerSize)
        if (header != KryoHeaderV0_1) {
            throw KryoException("Serialized bytes header does not match expected format.")
        }
        Input(byteSequence.bytes, byteSequence.offset + headerSize, byteSequence.size - headerSize).use { input ->
            return pool.run { kryo ->
                withContext(kryo, context) {
                    if (context.objectReferencesEnabled) {
                        uncheckedCast(kryo.readClassAndObject(input))
                    } else {
                        kryo.withoutReferences { uncheckedCast<Any?, T>(kryo.readClassAndObject(input)) }
                    }
                }
            }
        }
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        val pool = getPool(context)
        return pool.run { kryo ->
            withContext(kryo, context) {
                serializeOutputStreamPool.run { stream ->
                    serializeBufferPool.run { buffer ->
                        Output(buffer).use {
                            it.outputStream = stream
                            it.writeBytes(KryoHeaderV0_1.bytes)
                            if (context.objectReferencesEnabled) {
                                kryo.writeClassAndObject(it, obj)
                            } else {
                                kryo.withoutReferences { kryo.writeClassAndObject(it, obj) }
                            }
                        }
                        SerializedBytes(stream.toByteArray())
                    }
                }
            }
        }
    }
}

private val serializeBufferPool = LazyPool(
        newInstance = { ByteArray(64 * 1024) }
)

private val serializeOutputStreamPool = LazyPool(
        clear = ByteArrayOutputStream::reset,
        shouldReturnToPool = { it.size() < 256 * 1024 }, // Discard if it grew too large
        newInstance = { ByteArrayOutputStream(64 * 1024) }
)
