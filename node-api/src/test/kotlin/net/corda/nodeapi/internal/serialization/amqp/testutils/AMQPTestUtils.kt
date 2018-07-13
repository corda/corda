package net.corda.nodeapi.internal.serialization.amqp.testutils

import net.corda.core.internal.copyTo
import net.corda.core.internal.div

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.OpaqueBytes
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.EmptyWhitelist
import net.corda.nodeapi.internal.serialization.amqp.*
import net.corda.testing.common.internal.ProjectStructure
import org.apache.qpid.proton.codec.Data
import org.junit.Test
import java.io.File.separatorChar
import java.io.NotSerializableException
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

fun testDefaultFactory() = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())

fun testDefaultFactoryNoEvolution(): SerializerFactory {
    return SerializerFactory(
            AllWhitelist,
            ClassLoader.getSystemClassLoader(),
            evolutionSerializerGetter = EvolutionSerializerGetterTesting())
}

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

    @Throws(NotSerializableException::class)
    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        try {
            return _serialize(obj)
        } finally {
            andFinally()
        }
    }
}

fun testName(): String {
    val classLoader = Thread.currentThread().contextClassLoader
    return Thread.currentThread().stackTrace.first {
        try {
            classLoader.loadClass(it.className).getMethod(it.methodName).isAnnotationPresent(Test::class.java)
        } catch (e: Exception) {
            false
        }
    }.methodName
}

fun Any.testResourceName(): String = "${javaClass.simpleName}.${testName()}"

fun Any.writeTestResource(bytes: OpaqueBytes) {
    val dir = ProjectStructure.projectRootDir / "node-api" / "src" / "test" / "resources" / javaClass.`package`.name.replace('.', separatorChar)
    bytes.open().copyTo(dir / testResourceName(), REPLACE_EXISTING)
}

fun Any.readTestResource(): ByteArray = javaClass.getResourceAsStream(testResourceName()).readBytes()

@Throws(NotSerializableException::class)
inline fun <reified T : Any> DeserializationInput.deserializeAndReturnEnvelope(
        bytes: SerializedBytes<T>,
        context: SerializationContext? = null
) : ObjectAndEnvelope<T> {
    return deserializeAndReturnEnvelope(bytes, T::class.java)
}

@Throws(NotSerializableException::class)
inline fun <reified T : Any> DeserializationInput.deserialize(
        bytes: SerializedBytes<T>,
        context: SerializationContext? = null
)  : T = deserialize(bytes, T::class.java)


@Throws(NotSerializableException::class)
fun <T : Any> SerializationOutput.serializeAndReturnSchema(
        obj: T, context: SerializationContext? = null
): BytesAndSchemas<T> = serializeAndReturnSchema(obj)


@Throws(NotSerializableException::class)
fun <T : Any> SerializationOutput.serialize(obj: T): SerializedBytes<T> {
    try {
        return _serialize(obj)
    } finally {
        andFinally()
    }
}
