@file:JvmName("SharedContexts")
@file:DeleteForDJVM
package net.corda.serialization.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.serialization.*
import net.corda.serialization.internal.CordaSerializationEncoding.SNAPPY
import net.corda.serialization.internal.amqp.amqpMagic

val AMQP_P2P_CONTEXT = SerializationContextImpl(
        amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.P2P,
        null
)

@KeepForDJVM
object AlwaysAcceptEncodingWhitelist : EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding) = true
}

object QuasarWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = true
}
