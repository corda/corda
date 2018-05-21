package net.corda.serialization.internal.amqp.testutils

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.amqp.amqpMagic

val serializationProperties: MutableMap<Any, Any> = mutableMapOf()

val testSerializationContext = SerializationContextImpl(
        preferredSerializationVersion = amqpMagic,
        deserializationClassLoader = ClassLoader.getSystemClassLoader(),
        whitelist = AllWhitelist,
        properties = serializationProperties,
        objectReferencesEnabled = false,
        useCase = SerializationContext.UseCase.Testing,
        encoding = null)