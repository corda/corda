package net.corda.core.serialization

interface SerializationCustomization {
    fun addToWhitelist(type: Class<*>)
}

