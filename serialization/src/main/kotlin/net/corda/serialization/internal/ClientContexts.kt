@file:NonDeterministic
@file:JvmName("ClientContexts")
package net.corda.serialization.internal

import net.corda.core.NonDeterministic
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.serialization.internal.amqp.amqpMagic

/*
 * Serialisation contexts for the client.
 * These have been refactored into a separate file to prevent
 * servers from trying to instantiate any of them.
 */


val AMQP_RPC_CLIENT_CONTEXT = SerializationContextImpl(
        amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.RPCClient,
        null
)
