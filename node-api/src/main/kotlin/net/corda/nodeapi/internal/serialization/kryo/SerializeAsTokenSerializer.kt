package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.internal.castIfPossible
import net.corda.core.serialization.*
import net.corda.core.serialization.SingletonSerializationToken.Companion.singletonSerializationToken
import net.corda.core.serialization.internal.NonSingletonSerializeAsToken
import net.corda.core.serialization.internal.SerializationProperties
import net.corda.core.serialization.internal.SerializationToken
import net.corda.nodeapi.internal.serialization.SerializeAsTokenContextKey
import kotlin.reflect.KClass

abstract class SerializeAsTokenSerializer<T : Any, K : Any>(private val tokenType: KClass<K>) : Serializer<T>() {
    protected abstract fun toToken(kryo: Kryo, obj: T): K
    override fun write(kryo: Kryo, output: Output, obj: T) {
        kryo.writeClassAndObject(output, toToken(kryo, obj))
    }

    protected abstract fun fromToken(kryo: Kryo, type: Class<T>, token: K): T
    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        val token = tokenType.java.castIfPossible(kryo.readClassAndObject(input)) ?: throw KryoException("Non-token read for tokenized type: ${type.name}")
        val obj = fromToken(kryo, type, token)
        return type.castIfPossible(obj) ?: throw KryoException("Token read ($token) did not return expected tokenized type: ${type.name}")
    }
}

/** A Kryo serializer for [SerializeAsToken] implementations. */
object SingletonSerializeAsTokenSerializer : SerializeAsTokenSerializer<SerializeAsToken, SingletonSerializationToken>(SingletonSerializationToken::class) {
    override fun toToken(kryo: Kryo, obj: SerializeAsToken): SingletonSerializationToken {
        val context = kryo.serializationProperty(SerializeAsTokenContextKey) ?: throw KryoException("Attempt to write a ${SerializeAsToken::class.simpleName} instance of ${obj.javaClass.name} without initialising a context")
        context.putSingleton(obj)
        return singletonSerializationToken(obj.javaClass)
    }

    override fun fromToken(kryo: Kryo, type: Class<SerializeAsToken>, token: SingletonSerializationToken): SerializeAsToken {
        val context = kryo.serializationProperty(SerializeAsTokenContextKey) ?: throw KryoException("Attempt to read a token for a ${SerializeAsToken::class.simpleName} instance of ${type.name} without initialising a context")
        return context.getSingleton(token.className)
    }
}

object NonSingletonSerializeAsTokenSerializer : SerializeAsTokenSerializer<NonSingletonSerializeAsToken, SerializationToken<*>>(SerializationToken::class) {
    override fun toToken(kryo: Kryo, obj: NonSingletonSerializeAsToken): SerializationToken<*> {
        return obj.toToken()
    }

    override fun fromToken(kryo: Kryo, type: Class<NonSingletonSerializeAsToken>, token: SerializationToken<*>): NonSingletonSerializeAsToken {
        return token.fromToken(kryo.context[SerializationPropertiesKey] as SerializationProperties)
    }
}