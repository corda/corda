package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import net.corda.core.serialization.SerializationCustomization

class KryoSerializationCustomization(val kryo: Kryo) : SerializationCustomization {
    override fun addToWhitelist(vararg types: Class<*>) {
        require(types.toSet().size == types.size) {
            "Cannot add duplicate classes to the whitelist."
        }
        for (type in types) {
            ((kryo.classResolver as? CordaClassResolver)?.whitelist as? MutableClassWhitelist)?.add(type)
        }
    }
}