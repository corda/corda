package net.corda.contracts.serialization.missing.custom

//import net.corda.contracts.serialization.missing.contract.Data
import net.corda.core.serialization.SerializationWhitelist

@Suppress("unused")
class MissingSerializerRegistry : SerializationWhitelist {
    override val whitelist: List<Class<*>> = emptyList()//listOf(Data::class.java)
}
