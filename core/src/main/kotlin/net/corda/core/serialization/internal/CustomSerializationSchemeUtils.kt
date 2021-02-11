package net.corda.core.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.SerializationMagic
import net.corda.core.utilities.ByteSequence
import java.nio.ByteBuffer

class CustomSerializationSchemeUtils {

    @KeepForDJVM
    companion object {

        private const val SERIALIZATION_SCHEME_ID_SIZE = 4
        private val PREFIX = "CUS".toByteArray()

        fun getCustomSerializationMagicFromSchemeId(schemeId: Int) : SerializationMagic {
            return SerializationMagic.of(PREFIX + ByteBuffer.allocate(SERIALIZATION_SCHEME_ID_SIZE).putInt(schemeId).array())
        }

        fun getSchemeIdIfCustomSerializationMagic(magic: SerializationMagic): Int? {
            return if (magic.take(PREFIX.size) != ByteSequence.of(PREFIX)) {
                null
            } else {
                return magic.slice(start = PREFIX.size).int
            }
        }
    }
}