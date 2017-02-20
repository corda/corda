package net.corda.core.schemas.requery.converters

import io.requery.Converter
import net.corda.core.crypto.SecureHash

/**
 * Convert from a [SecureHash] to a [String]
 */
class SecureHashConverter : Converter<SecureHash, String> {

    override fun getMappedType(): Class<SecureHash> { return SecureHash::class.java }

    override fun getPersistedType(): Class<String> { return String::class.java }

    override fun getPersistedSize(): Int? { return null }

    override fun convertToPersisted(value: SecureHash?): String? {
        return value?.toString()
    }

    override fun convertToMapped(type: Class<out SecureHash>?, value: String?): SecureHash? {
        return value?.let { SecureHash.parse(value) }
    }
}