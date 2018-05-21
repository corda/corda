/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("ServerContexts")

package net.corda.serialization.internal

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.serialization.internal.kryo.BuiltInExceptionsWhitelist
import net.corda.serialization.internal.kryo.GlobalTransientClassWhiteList
import net.corda.serialization.internal.kryo.kryoMagic

/*
 * Serialisation contexts for the server.
 * These have been refactored into a separate file to prevent
 * clients from trying to instantiate any of them.
 *
 * NOTE: The [KRYO_STORAGE_CONTEXT] and [AMQP_STORAGE_CONTEXT]
 * CANNOT always be instantiated outside of the server and so
 * MUST be kept separate!
 */


val AMQP_STORAGE_CONTEXT = SerializationContextImpl(amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        AllButBlacklisted,
        emptyMap(),
        true,
        SerializationContext.UseCase.Storage,
        null,
        AlwaysAcceptEncodingWhitelist)

val AMQP_RPC_SERVER_CONTEXT = SerializationContextImpl(amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.RPCServer,
        null)
