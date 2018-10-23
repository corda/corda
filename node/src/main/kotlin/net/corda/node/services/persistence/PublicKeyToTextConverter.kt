package net.corda.node.services.persistence

import net.corda.core.crypto.Crypto
import net.corda.core.utilities.hexToByteArray
import net.corda.core.utilities.toHex
import java.security.PublicKey
import javax.persistence.AttributeConverter
import javax.persistence.Converter

/**
 * Converts to and from a Public key into a hex encoded string.
 * Used by JPA to automatically map a [PublicKey] to a text column
 */
@Converter(autoApply = true)
class PublicKeyToTextConverter : AttributeConverter<PublicKey, String> {
    override fun convertToDatabaseColumn(key: PublicKey?): String? = key?.encoded?.toHex()
    override fun convertToEntityAttribute(text: String?): PublicKey? = text?.let { Crypto.decodePublicKey(it.hexToByteArray()) }
}