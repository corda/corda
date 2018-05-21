package net.corda.node.internal.serialization.testutils

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.serialization.internal.AllWhitelist

val serializationProperties: MutableMap<Any, Any> = mutableMapOf()

val serializationContext = SerializationContextImpl(
        preferredSerializationVersion = amqpMagic,
        deserializationClassLoader = ClassLoader.getSystemClassLoader(),
        whitelist = AllWhitelist,
        properties = serializationProperties,
        objectReferencesEnabled = false,
        useCase = SerializationContext.UseCase.Testing,
        encoding = null)