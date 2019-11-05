package net.corda.serialization.djvm.deserializers

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.function.Function

class InputStreamDeserializer : Function<ByteArray, InputStream?> {
    override fun apply(bytes: ByteArray): InputStream? {
        return ByteArrayInputStream(bytes)
    }
}
