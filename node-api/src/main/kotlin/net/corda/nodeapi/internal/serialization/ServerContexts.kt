@file:JvmName("ServerContexts")

package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0

/*
 * Serialisation contexts for the server.
 * These have been refactored into a separate file to prevent
 * clients from trying to instantiate any of them.
 *
 * NOTE: The [KRYO_STORAGE_CONTEXT] and [AMQP_STORAGE_CONTEXT]
 * CANNOT always be instantiated outside of the server and so
 * MUST be kept separate!
 */
val KRYO_RPC_SERVER_CONTEXT = SerializationContextImpl(KryoHeaderV0_1,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.RPCServer)
val KRYO_STORAGE_CONTEXT = SerializationContextImpl(KryoHeaderV0_1,
        SerializationDefaults.javaClass.classLoader,
        AllButBlacklisted,
        emptyMap(),
        true,
        SerializationContext.UseCase.Storage)
val KRYO_CHECKPOINT_CONTEXT = SerializationContextImpl(KryoHeaderV0_1,
        SerializationDefaults.javaClass.classLoader,
        QuasarWhitelist,
        emptyMap(),
        true,
        SerializationContext.UseCase.Checkpoint)

object QuasarWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = true
}

val AMQP_STORAGE_CONTEXT = SerializationContextImpl(AmqpHeaderV1_0,
        SerializationDefaults.javaClass.classLoader,
        AllButBlacklisted,
        emptyMap(),
        true,
        SerializationContext.UseCase.Storage)
