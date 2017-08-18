package net.corda.nodeapi.internal.serialization.amqp

import org.apache.qpid.proton.codec.Data
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.EmptyWhitelist

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
