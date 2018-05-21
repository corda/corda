/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("SharedContexts")

package net.corda.serialization.internal

import net.corda.core.serialization.*
import net.corda.serialization.internal.CordaSerializationEncoding.SNAPPY
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.serialization.internal.kryo.BuiltInExceptionsWhitelist
import net.corda.serialization.internal.kryo.GlobalTransientClassWhiteList
import net.corda.serialization.internal.kryo.kryoMagic

/*
 * Serialisation contexts shared by the server and client.
 *
 * NOTE: The [KRYO_STORAGE_CONTEXT] and [AMQP_STORAGE_CONTEXT]
 * CANNOT always be instantiated outside of the server and so
 * MUST be kept separate from these ones!
 */
val KRYO_CHECKPOINT_CONTEXT = SerializationContextImpl(kryoMagic,
        SerializationDefaults.javaClass.classLoader,
        QuasarWhitelist,
        emptyMap(),
        true,
        SerializationContext.UseCase.Checkpoint,
        SNAPPY,
        AlwaysAcceptEncodingWhitelist)

val AMQP_P2P_CONTEXT = SerializationContextImpl(amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.P2P,
        null)

internal object AlwaysAcceptEncodingWhitelist : EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding) = true
}

object QuasarWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = true
}
