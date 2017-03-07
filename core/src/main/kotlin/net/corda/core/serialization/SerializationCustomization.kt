package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo

interface SerializationCustomization {
    fun addToWhitelist(type: Class<*>)
}

class KryoSerializationCustomization(val kryo: Kryo) : SerializationCustomization {
    override fun addToWhitelist(type: Class<*>) {
        kryo.addToWhitelist(type)
    }
}