package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import net.corda.core.serialization.SerializationCustomization

class KryoSerializationCustomization(val kryo: Kryo) : SerializationCustomization {
    fun Kryo.addToWhitelist(vararg types: Class<*>) {
        for (type in types) {
            ((classResolver as? CordaClassResolver)?.whitelist as? MutableClassWhitelist)?.add(type)
        }
    }

    override fun addToWhitelist(vararg types: Class<*>) {
        kryo.addToWhitelist(*types)
    }
}