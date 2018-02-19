@file:JvmName("ServerContexts")

package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.ContextPropertyKeys
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.nodeapi.internal.serialization.amqp.amqpMagic

object QuasarWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = true
}

/*
 * Serialisation contexts for the server.
 * These have been refactored into a separate file to prevent
 * clients from trying to instantiate any of them.
 *
 * NOTE: The [AMQP_STORAGE_CONTEXT] CANNOT always be instantiated
 * outside of the server and soMUST be kept separate!
 */

val AMQP_STORAGE_CONTEXT = SerializationContextImpl(amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        AllButBlacklisted,
        emptyMap(),
        true,
        SerializationContext.UseCase.Storage,
        null,
        AlwaysAcceptEncodingWhitelist)

val AMQP_RPC_SERVER_CONTEXT = SerializationContextImpl(
        amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        mapOf(ContextPropertyKeys.SERIALIZERS to emptyList<Class<*>>()),
        true,
        SerializationContext.UseCase.RPCServer,
        null)
