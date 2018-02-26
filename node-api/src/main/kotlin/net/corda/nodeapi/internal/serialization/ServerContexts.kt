@file:JvmName("ServerContexts")

package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.nodeapi.internal.serialization.amqp.amqpMagic
import net.corda.nodeapi.internal.serialization.kryo.kryoMagic

object QuasarWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = true
}

/*
 * Serialisation contexts for the server.
 * These have been refactored into a separate file to prevent
 * clients from trying to instantiate any of them.
 *
 * NOTE: The [KRYO_STORAGE_CONTEXT] and [AMQP_STORAGE_CONTEXT]
 * CANNOT always be instantiated outside of the server and so
 * MUST be kept separate!
 */

val KRYO_RPC_SERVER_CONTEXT = SerializationContextImpl(kryoMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.RPCServer)
val KRYO_STORAGE_CONTEXT = SerializationContextImpl(kryoMagic,
        SerializationDefaults.javaClass.classLoader,
        AllButBlacklisted,
        emptyMap(),
        true,
        SerializationContext.UseCase.Storage)
val AMQP_STORAGE_CONTEXT = SerializationContextImpl(amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        AllButBlacklisted,
        emptyMap(),
        true,
        SerializationContext.UseCase.Storage)
val AMQP_RPC_SERVER_CONTEXT = SerializationContextImpl(amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.RPCServer)
