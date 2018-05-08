/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.kryo

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.ByteBufferInputStream
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.SectionId
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

val kryoMagic = CordaSerializationMagic("corda".toByteArray() + byteArrayOf(0, 0))

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

    private fun <T : Any> SerializationContext.kryo(task: Kryo.() -> T): T {
        return getPool(this).run { kryo ->
            kryo.context.ensureCapacity(properties.size)
            properties.forEach { kryo.context.put(it.key, it.value) }
            try {
                kryo.task()
            } finally {
                kryo.context.clear()
            }
        }
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        val dataBytes = kryoMagic.consume(byteSequence)
                ?: throw KryoException("Serialized bytes header does not match expected format.")
        return context.kryo {
            kryoInput(ByteBufferInputStream(dataBytes)) {
                val result: T
                loop@ while (true) {
                    when (SectionId.reader.readFrom(this)) {
                        SectionId.ENCODING -> {
                            val encoding = CordaSerializationEncoding.reader.readFrom(this)
                            context.encodingWhitelist.acceptEncoding(encoding) || throw KryoException(encodingNotPermittedFormat.format(encoding))
                            substitute(encoding::wrap)
                        }
                        SectionId.DATA_AND_STOP, SectionId.ALT_DATA_AND_STOP -> {
                            result = if (context.objectReferencesEnabled) {
                                uncheckedCast(readClassAndObject(this))
                            } else {
                                withoutReferences { uncheckedCast<Any?, T>(readClassAndObject(this)) }
                            }
                            break@loop
                        }
                    }
                }
                result
            }
        }
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        return context.kryo {
            SerializedBytes(kryoOutput {
                kryoMagic.writeTo(this)
                context.encoding?.let { encoding ->
                    SectionId.ENCODING.writeTo(this)
                    (encoding as CordaSerializationEncoding).writeTo(this)
                    substitute(encoding::wrap)
                }
                SectionId.ALT_DATA_AND_STOP.writeTo(this) // Forward-compatible in null-encoding case.
                if (context.objectReferencesEnabled) {
                    writeClassAndObject(this, obj)
                } else {
                    withoutReferences { writeClassAndObject(this, obj) }
                }
            })
        }
    }
}
