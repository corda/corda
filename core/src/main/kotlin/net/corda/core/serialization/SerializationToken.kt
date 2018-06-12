/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.serialization

import net.corda.core.DeleteForDJVM
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializationToken.Companion.singletonSerializationToken

/**
 * The interfaces and classes in this file allow large, singleton style classes to
 * mark themselves as needing converting to some form of token representation in the serialised form
 * and converting back again when deserializing.
 *
 * Typically these classes would be used for node services and subsystems that might become reachable from
 * Fibers and thus sucked into serialization when they are checkpointed.
 */

/**
 * This interface should be implemented by classes that want to substitute a token representation of themselves if
 * they are serialized because they have a lot of internal state that does not serialize (well).
 *
 * This models a similar pattern to the readReplace/writeReplace methods in Java serialization.
 */
@DeleteForDJVM
@CordaSerializable
interface SerializeAsToken {
    fun toToken(context: SerializeAsTokenContext): SerializationToken
}

/**
 * This represents a token in the serialized stream for an instance of a type that implements [SerializeAsToken].
 */
@DeleteForDJVM
interface SerializationToken {
    fun fromToken(context: SerializeAsTokenContext): Any
}

/**
 * A context for mapping SerializationTokens to/from SerializeAsTokens.
 */
@DeleteForDJVM
interface SerializeAsTokenContext {
    val serviceHub: ServiceHub
    fun putSingleton(toBeTokenized: SerializeAsToken)
    fun getSingleton(className: String): SerializeAsToken
}

/**
 * A class representing a [SerializationToken] for some object that is not serializable but can be looked up
 * (when deserialized) via just the class name.
 */
@DeleteForDJVM
class SingletonSerializationToken private constructor(private val className: String) : SerializationToken {

    override fun fromToken(context: SerializeAsTokenContext) = context.getSingleton(className)

    fun registerWithContext(context: SerializeAsTokenContext, toBeTokenized: SerializeAsToken) = also { context.putSingleton(toBeTokenized) }

    companion object {
        fun <T : SerializeAsToken> singletonSerializationToken(toBeTokenized: Class<T>) = SingletonSerializationToken(toBeTokenized.name)
    }
}

/**
 * A base class for implementing large objects / components / services that need to serialize themselves to a string token
 * to indicate which instance the token is a serialized form of.
 */
@DeleteForDJVM
abstract class SingletonSerializeAsToken : SerializeAsToken {
    private val token = singletonSerializationToken(javaClass)

    override fun toToken(context: SerializeAsTokenContext) = token.registerWithContext(context, this)
}
