package net.corda.contracts.serialization.missing.custom

import net.corda.contracts.serialization.missing.contract.CustomData
import net.corda.core.serialization.SerializationWhitelist

@Suppress("unused")
class MissingSerializerRegistry : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(CustomData::class.java)
}
