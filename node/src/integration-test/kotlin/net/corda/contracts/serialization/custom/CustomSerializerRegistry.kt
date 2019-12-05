package net.corda.contracts.serialization.custom

import net.corda.core.serialization.SerializationWhitelist

@Suppress("unused")
class CustomSerializerRegistry : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(Currantsy::class.java)
}
