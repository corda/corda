package net.corda.serialization.internal.amqp

import net.corda.core.internal.InputStreamAndHash
import net.corda.serialization.internal.amqp.custom.InputStreamSerializer
import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import org.junit.Test
import java.io.FilterInputStream
import java.io.InputStream

class StreamTests {

    private class WrapperStream(input: InputStream) : FilterInputStream(input)

    @Test
    fun inputStream() {
        val attachment = InputStreamAndHash.createInMemoryTestZip(2116, 1)
        val id : InputStream = WrapperStream(attachment.inputStream)

        val serializerFactory = testDefaultFactory().apply {
            register(InputStreamSerializer)
        }

        val bytes = TestSerializationOutput(true, serializerFactory).serialize(id)

        val deserializerFactory = testDefaultFactory().apply {
            register(InputStreamSerializer)
        }

        DeserializationInput(serializerFactory).deserialize(bytes)
        DeserializationInput(deserializerFactory).deserialize(bytes)
    }

    @Test
    fun listInputStream() {
        val attachment = InputStreamAndHash.createInMemoryTestZip(2116, 1)
        val id /* : List<InputStream> */= listOf(WrapperStream(attachment.inputStream))

        val serializerFactory = testDefaultFactory().apply {
            register(InputStreamSerializer)
        }

        val bytes = TestSerializationOutput(true, serializerFactory).serialize(id)

        val deserializerFactory = testDefaultFactory().apply {
            register(InputStreamSerializer)
        }

        DeserializationInput(serializerFactory).deserialize(bytes)
        DeserializationInput(deserializerFactory).deserialize(bytes)
    }
}