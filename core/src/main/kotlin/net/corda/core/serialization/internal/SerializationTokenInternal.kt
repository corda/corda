package net.corda.core.serialization.internal

import net.corda.core.serialization.SerializeAsToken

/** Like [SerializeAsToken] but actually uses a token rather than the class name. */
interface NonSingletonSerializeAsToken {
    fun toToken(): SerializationToken<*>
}

@Suppress("unused")
interface SerializationPropertyKey<V>

interface SerializationProperties {
    operator fun <V : Any> get(key: SerializationPropertyKey<V>): V?
}

/** This represents a token in the serialized stream for an instance of a type that implements [NonSingletonSerializeAsToken]. */
interface SerializationToken<out T : NonSingletonSerializeAsToken> {
    fun fromToken(properties: SerializationProperties): T
}
