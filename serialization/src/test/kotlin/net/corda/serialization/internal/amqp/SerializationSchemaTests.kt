package net.corda.serialization.internal.amqp

import net.corda.core.serialization.*
import net.corda.serialization.internal.BuiltInExceptionsWhitelist
import net.corda.serialization.internal.GlobalTransientClassWhiteList
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.amqp.schema.amqpMagic

// Make sure all serialization calls in this test don't get stomped on by anything else
val TESTING_CONTEXT = SerializationContextImpl(amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        SerializationContext.UseCase.Testing,
        null)