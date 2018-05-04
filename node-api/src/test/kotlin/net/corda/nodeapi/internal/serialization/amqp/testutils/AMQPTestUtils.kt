package net.corda.nodeapi.internal.serialization.amqp.testutils

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import org.apache.qpid.proton.codec.Data
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.EmptyWhitelist
import net.corda.nodeapi.internal.serialization.amqp.*
import java.io.NotSerializableException

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


@Throws(NotSerializableException::class)
inline fun <reified T : Any> DeserializationInput.deserializeAndReturnEnvelope(
        bytes: SerializedBytes<T>,
        context: SerializationContext? = null
) : ObjectAndEnvelope<T> {
    return deserializeAndReturnEnvelope(bytes, T::class.java,
            context ?: testSerializationContext)
}

@Throws(NotSerializableException::class)
inline fun <reified T : Any> DeserializationInput.deserialize(
        bytes: SerializedBytes<T>,
        context: SerializationContext? = null
)  : T = deserialize(bytes, T::class.java, context ?: testSerializationContext)


@Throws(NotSerializableException::class)
fun <T : Any> SerializationOutput.serializeAndReturnSchema(
        obj: T, context: SerializationContext? = null
): BytesAndSchemas<T> = serializeAndReturnSchema(obj, context ?: testSerializationContext)


@Throws(NotSerializableException::class)
fun <T : Any> SerializationOutput.serialize(obj: T): SerializedBytes<T> {
    try {
        return _serialize(obj, testSerializationContext)
    } finally {
        andFinally()
    }
}
