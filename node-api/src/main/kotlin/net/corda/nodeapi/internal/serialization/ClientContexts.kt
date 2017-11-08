@file:JvmName("ClientContexts")

package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0
import net.corda.nodeapi.internal.serialization.kryo.KryoHeaderV0_1

/*
 * Serialisation contexts for the client.
 * These have been refactored into a separate file to prevent
 * servers from trying to instantiate any of them.
 */

val KRYO_RPC_CLIENT_CONTEXT = SerializationContextImpl(KryoHeaderV0_1,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.RPCClient)

val AMQP_RPC_CLIENT_CONTEXT = SerializationContextImpl(AmqpHeaderV1_0,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.RPCClient)
