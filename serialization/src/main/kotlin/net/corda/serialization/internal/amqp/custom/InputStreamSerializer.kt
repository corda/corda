package net.corda.serialization.internal.amqp.custom

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.codec.Data
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Type

/**
 * A serializer that writes out the content of an input stream as bytes and deserializes into a [ByteArrayInputStream].
 */
object InputStreamSerializer : CustomSerializer.Implements<InputStream>(InputStream::class.java) {
    override val schemaForDocumentation = Schema(
            listOf(
                    RestrictedType(
                            type.toString(),
                            "",
                            listOf(type.toString()),
                            AMQPTypeIdentifiers.primitiveTypeName(ByteArray::class.java),
                            descriptor,
                            emptyList())))

    override fun writeDescribedObject(obj: InputStream, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext
    ) {
        val startingSize = maxOf(4096, obj.available() + 1)
        var buffer = ByteArray(startingSize)
        var pos = 0
        while (true) {
            val numberOfBytesRead = obj.read(buffer, pos, buffer.size - pos)
            if (numberOfBytesRead != -1) {
                pos += numberOfBytesRead
                // If the buffer is now full, resize it.
                if (pos == buffer.size) {
                    buffer = buffer.copyOf(buffer.size + maxOf(4096, obj.available() + 1))
                }
            } else {
                data.putBinary(Binary(buffer, 0, pos))
                break
            }
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ) : InputStream {
        val bits = input.readObject(obj, schemas, ByteArray::class.java, context) as ByteArray
        return bits.inputStream()
    }
}