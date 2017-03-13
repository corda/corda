package net.corda.core.schemas.requery.converters

import io.requery.Converter
import net.corda.core.crypto.SecureHash

/**
 * Convert from a [SecureHash] to a [String]
 */
class SecureHashConverter : Converter<SecureHash, String> {

    override fun getMappedType(): Class<SecureHash> = SecureHash::class.java

    override fun getPersistedType(): Class<String> = String::class.java

    /**
     * SecureHash consists of 32 bytes which need VARCHAR(64) in hex
     * TODO: think about other hash widths
     */
    override fun getPersistedSize(): Int? = 64

    override fun convertToPersisted(value: SecureHash?): String? {
        return value?.toString()
    }

    override fun convertToMapped(type: Class<out SecureHash>, value: String?): SecureHash? {
        return value?.let { SecureHash.parse(value) }
    }
}