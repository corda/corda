@file:JvmName("HexadecimalSerialisers")
package net.corda.attestation.message.ias

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.corda.attestation.hexToBytes
import net.corda.attestation.toHexString

class HexadecimalSerialiser : StdSerializer<ByteArray>(ByteArray::class.java) {
    override fun serialize(value: ByteArray, gen: JsonGenerator, provider: SerializerProvider) = gen.writeString(value.toHexString())
}

class HexadecimalDeserialiser : StdDeserializer<ByteArray>(ByteArray::class.java) {
    override fun deserialize(p: JsonParser, context: DeserializationContext) = p.valueAsString.hexToBytes()
}
