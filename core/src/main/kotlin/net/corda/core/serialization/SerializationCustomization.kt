package net.corda.core.serialization

interface SerializationCustomization {
    fun addToWhitelist(vararg types: Class<*>)
}

