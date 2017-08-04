package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data

class TestSerializationOutput(
        private val verbose: Boolean,
        serializerFactory: SerializerFactory = SerializerFactory()) : SerializationOutput(serializerFactory) {

    override fun writeSchema(schema: Schema, data: Data) {
        if (verbose) println(schema)
        super.writeSchema(schema, data)
    }
}

