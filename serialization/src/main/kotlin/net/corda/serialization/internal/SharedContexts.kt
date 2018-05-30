@file:JvmName("SharedContexts")
@file:NonDeterministic
package net.corda.serialization.internal

import net.corda.core.Deterministic
import net.corda.core.NonDeterministic
import net.corda.core.serialization.*
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

@Deterministic
object AlwaysAcceptEncodingWhitelist : EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding) = true
}

object QuasarWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = true
}
