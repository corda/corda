package net.corda.nodeapi.internal.serialization.amqp

import org.apache.qpid.proton.codec.Data
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.EmptyWhitelist

fun testDefaultFactory() = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
fun testDefaultFactoryNoEvolution() = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader(),
        EvolutionSerializerGetterTesting())
fun testDefaultFactoryWithWhitelist() = SerializerFactory(EmptyWhitelist, ClassLoader.getSystemClassLoader())

class TestSerializationOutput(
        private val verbose: Boolean,
        serializerFactory: SerializerFactory = testDefaultFactory())
    : SerializationOutput(serializerFactory) {

    override fun writeSchema(schema: Schema, data: Data) {
        if (verbose) println(schema)
        super.writeSchema(schema, data)
    }

    override fun writeTransformSchema(transformsSchema: TransformsSchema, data: Data) {
        if(verbose) {
            println ("Writing Transform Schema")
            println (transformsSchema)
        }
        super.writeTransformSchema(transformsSchema, data)
    }
}

fun testName(): String = Thread.currentThread().stackTrace[2].methodName

