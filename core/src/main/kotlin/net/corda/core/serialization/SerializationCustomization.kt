package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo

interface SerializationCustomization {
    fun addToWhitelist(type: Class<*>)
    fun addToBlacklist(type: Class<*>)
}

class KryoSerializationCustomization(val kryo: Kryo) : SerializationCustomization {
    override fun addToWhitelist(type: Class<*>) {
        kryo.addToWhitelist(type)
    }

    override fun addToBlacklist(type: Class<*>) {
        kryo.addToBlacklist(type)
    }
}