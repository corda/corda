package net.corda.contracts.serialization.custom

import net.corda.core.serialization.SerializationWhitelist

class CustomSerializerRegistry : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(Currantsy::class.java)
}
