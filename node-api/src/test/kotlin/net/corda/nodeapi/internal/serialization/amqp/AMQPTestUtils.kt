package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializedBytes
import org.apache.qpid.proton.codec.Data
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.EmptyWhitelist
import java.io.NotSerializableException

fun testDefaultFactory() = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
fun testDefaultFactoryWithWhitelist() = SerializerFactory(EmptyWhitelist, ClassLoader.getSystemClassLoader())

class TestSerializationOutput(
        private val verbose: Boolean,
        serializerFactory: SerializerFactory = testDefaultFactory())
    : SerializationOutput(serializerFactory) {

    override fun writeSchema(schema: Schema, data: Data) {
        if (verbose) println(schema)
        super.writeSchema(schema, data)
    }
}

fun testName(): String = Thread.currentThread().stackTrace[2].methodName

data class BytesAndSchema<T : Any>(val obj: SerializedBytes<T>, val schema: Schema)

// Extension for the serialize routine that returns the scheme encoded into the
// bytes as well as the bytes for simple testing
@Throws(NotSerializableException::class)
fun <T : Any> SerializationOutput.serializeAndReturnSchema(obj: T): BytesAndSchema<T> {
    try {
        return BytesAndSchema(_serialize(obj), Schema(schemaHistory.toList()))
    } finally {
        andFinally()
    }
}
