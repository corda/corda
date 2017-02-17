package net.corda.node.services.persistence

import io.requery.Converter
import net.corda.core.crypto.SecureHash

class SecureHashConverter : Converter<SecureHash, String> {
    override fun convertToMapped(type: Class<out SecureHash>?, value: String?): SecureHash? {
        if(value == null)
            return null
        else return SecureHash.parse(value)
    }

    override fun getMappedType(): Class<SecureHash> {
        return SecureHash::class.java
    }

    override fun getPersistedSize(): Int? {
        return null
    }

    override fun convertToPersisted(value: SecureHash?): String? {
        if(value == null)
            return null
        else
            return value.toString()
    }

    override fun getPersistedType(): Class<String> {
        return String::class.java
    }


}