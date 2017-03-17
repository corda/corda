package net.corda.core.serialization

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.esotericsoftware.kryo.serializers.JavaSerializer
import com.esotericsoftware.kryo.util.MapReferenceResolver
import com.google.common.annotations.VisibleForTesting
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.node.AttachmentsClassLoader
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.transactions.WireTransaction
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.time.Instant
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import kotlin.reflect.*
import kotlin.reflect.jvm.javaType

/**
 * Serialization utilities, using the Kryo framework with a custom serialiser for immutable data classes and a dead
 * simple, totally non-extensible binary (sub)format.
 *
 * This is NOT what should be used in any final platform product, rather, the final state should be a precisely
 * specified and standardised binary format with attention paid to anti-malleability, versioning and performance.
 * FIX SBE is a potential candidate: it prioritises performance over convenience and was designed for HFT. Google
 * Protocol Buffers with a minor tightening to make field reordering illegal is another possibility.
 *
 * FIX SBE:
 *     https://real-logic.github.io/simple-binary-encoding/
 *     http://mechanical-sympathy.blogspot.co.at/2014/05/simple-binary-encoding.html
 * Protocol buffers:
 *     https://developers.google.com/protocol-buffers/
 *
 * But for now we use Kryo to maximise prototyping speed.
 *
 * Note that this code ignores *ALL* concerns beyond convenience, in particular it ignores:
 *
 * - Performance
 * - Security
 *
 * This code will happily deserialise literally anything, including malicious streams that would reconstruct classes
 * in invalid states, thus violating system invariants. It isn't designed to handle malicious streams and therefore,
 * isn't usable beyond the prototyping stage. But that's fine: we can revisit serialisation technologies later after
 * a formal evaluation process.
 *
 * We now distinguish between internal, storage related Kryo and external, network facing Kryo.  We presently use
 * some non-whitelisted classes as part of internal storage.
 * TODO: eliminate internal, storage related whitelist issues, such as private keys in blob storage.
 */

// A convenient instance of Kryo pre-configured with some useful things. Used as a default by various functions.
fun p2PKryo(): KryoPool = kryoPool
// Same again, but this has whitelisting turned off for internal storage use only.
fun storageKryo(): KryoPool = internalKryoPool

/**
 * A type safe wrapper around a byte array that contains a serialised object. You can call [SerializedBytes.deserialize]
 * to get the original object back.
 */
class SerializedBytes<T : Any>(bytes: ByteArray, val internalOnly: Boolean = false) : OpaqueBytes(bytes) {
    // It's OK to use lazy here because SerializedBytes is configured to use the ImmutableClassSerializer.
    val hash: SecureHash by lazy { bytes.sha256() }

    fun writeToFile(path: Path): Path = Files.write(path, bytes)
}

// "corda" + majorVersionByte + minorVersionMSB + minorVersionLSB
private val KryoHeaderV0_1: OpaqueBytes = OpaqueBytes("corda\u0000\u0000\u0001".toByteArray())

// Some extension functions that make deserialisation convenient and provide auto-casting of the result.
fun <T : Any> ByteArray.deserialize(kryo: KryoPool = p2PKryo()): T {
    Input(this).use {
        val header = OpaqueBytes(it.readBytes(8))
        if (header != KryoHeaderV0_1) {
            throw KryoException("Serialized bytes header does not match any known format.")
        }
        @Suppress("UNCHECKED_CAST")
        return kryo.run { k -> k.readClassAndObject(it) as T }
    }
}

// TODO: The preferred usage is with a pool. Try and eliminate use of this from RPC.
fun <T : Any> ByteArray.deserialize(kryo: Kryo): T = deserialize(kryo.asPool())

fun <T : Any> OpaqueBytes.deserialize(kryo: KryoPool = p2PKryo()): T {
    return this.bytes.deserialize(kryo)
}

// The more specific deserialize version results in the bytes being cached, which is faster.
@JvmName("SerializedBytesWireTransaction")
fun SerializedBytes<WireTransaction>.deserialize(kryo: KryoPool = p2PKryo()): WireTransaction = WireTransaction.deserialize(this, kryo)

fun <T : Any> SerializedBytes<T>.deserialize(kryo: KryoPool = if (internalOnly) storageKryo() else p2PKryo()): T = bytes.deserialize(kryo)

fun <T : Any> SerializedBytes<T>.deserialize(kryo: Kryo): T = bytes.deserialize(kryo.asPool())

// Internal adapter for use when we haven't yet converted to a pool, or for tests.
private fun Kryo.asPool(): KryoPool = (KryoPool.Builder { this }.build())

/**
 * A serialiser that avoids writing the wrapper class to the byte stream, thus ensuring [SerializedBytes] is a pure
 * type safety hack.
 */
object SerializedBytesSerializer : Serializer<SerializedBytes<Any>>() {
    override fun write(kryo: Kryo, output: Output, obj: SerializedBytes<Any>) {
        output.writeVarInt(obj.bytes.size, true)
        output.writeBytes(obj.bytes)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<SerializedBytes<Any>>): SerializedBytes<Any> {
        return SerializedBytes(input.readBytes(input.readVarInt(true)))
    }
}

/**
 * Can be called on any object to convert it to a byte array (wrapped by [SerializedBytes]), regardless of whether
 * the type is marked as serializable or was designed for it (so be careful!).
 */
fun <T : Any> T.serialize(kryo: KryoPool = p2PKryo(), internalOnly: Boolean = false): SerializedBytes<T> {
    return kryo.run { k -> serialize(k, internalOnly) }
}

fun <T : Any> T.serialize(kryo: Kryo, internalOnly: Boolean = false): SerializedBytes<T> {
    val stream = ByteArrayOutputStream()
    Output(stream).use {
        it.writeBytes(KryoHeaderV0_1.bytes)
        kryo.writeClassAndObject(it, this)
    }
    return SerializedBytes(stream.toByteArray(), internalOnly)
}

/**
 * Serializes properties and deserializes by using the constructor. This assumes that all backed properties are
 * set via the constructor and the class is immutable.
 */
class ImmutableClassSerializer<T : Any>(val klass: KClass<T>) : Serializer<T>() {
    val props = klass.memberProperties.sortedBy { it.name }
    val propsByName = props.associateBy { it.name }
    val constructor = klass.primaryConstructor!!

    init {
        // Verify that this class is immutable (all properties are final)
        assert(props.none { it is KMutableProperty<*> })
    }

    // Just a utility to help us catch cases where nodes are running out of sync versions.
    private fun hashParameters(params: List<KParameter>): Int {
        return params.map {
            (it.name ?: "") + it.index.toString() + it.type.javaType.typeName
        }.hashCode()
    }

    override fun write(kryo: Kryo, output: Output, obj: T) {
        output.writeVarInt(constructor.parameters.size, true)
        output.writeInt(hashParameters(constructor.parameters))
        for (param in constructor.parameters) {
            val kProperty = propsByName[param.name!!]!!
            when (param.type.javaType.typeName) {
                "int" -> output.writeVarInt(kProperty.get(obj) as Int, true)
                "long" -> output.writeVarLong(kProperty.get(obj) as Long, true)
                "short" -> output.writeShort(kProperty.get(obj) as Int)
                "char" -> output.writeChar(kProperty.get(obj) as Char)
                "byte" -> output.writeByte(kProperty.get(obj) as Byte)
                "double" -> output.writeDouble(kProperty.get(obj) as Double)
                "float" -> output.writeFloat(kProperty.get(obj) as Float)
                else -> try {
                    kryo.writeClassAndObject(output, kProperty.get(obj))
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to serialize ${param.name} in ${klass.qualifiedName}", e)
                }
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        assert(type.kotlin == klass)
        val numFields = input.readVarInt(true)
        val fieldTypeHash = input.readInt()

        // A few quick checks for data evolution. Note that this is not guaranteed to catch every problem! But it's
        // good enough for a prototype.
        if (numFields != constructor.parameters.size)
            throw KryoException("Mismatch between number of constructor parameters and number of serialised fields " +
                    "for ${klass.qualifiedName} ($numFields vs ${constructor.parameters.size})")
        if (fieldTypeHash != hashParameters(constructor.parameters))
            throw KryoException("Hashcode mismatch for parameter types for ${klass.qualifiedName}: unsupported type evolution has happened.")

        val args = arrayOfNulls<Any?>(numFields)
        var cursor = 0
        for (param in constructor.parameters) {
            args[cursor++] = when (param.type.javaType.typeName) {
                "int" -> input.readVarInt(true)
                "long" -> input.readVarLong(true)
                "short" -> input.readShort()
                "char" -> input.readChar()
                "byte" -> input.readByte()
                "double" -> input.readDouble()
                "float" -> input.readFloat()
                else -> kryo.readClassAndObject(input)
            }
        }
        // If the constructor throws an exception, pass it through instead of wrapping it.
        return try {
            constructor.call(*args)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }
}

// TODO This is a temporary inefficient serialiser for sending InputStreams through RPC. This may be done much more
// efficiently using Artemis's large message feature.
object InputStreamSerializer : Serializer<InputStream>() {
    override fun write(kryo: Kryo, output: Output, stream: InputStream) {
        val buffer = ByteArray(4096)
        while (true) {
            val numberOfBytesRead = stream.read(buffer)
            if (numberOfBytesRead != -1) {
                output.writeInt(numberOfBytesRead, true)
                output.writeBytes(buffer, 0, numberOfBytesRead)
            } else {
                output.writeInt(0)
                break
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<InputStream>): InputStream {
        val chunks = ArrayList<ByteArray>()
        while (true) {
            val chunk = input.readBytesWithLength()
            if (chunk.isEmpty()) {
                break
            } else {
                chunks.add(chunk)
            }
        }
        val flattened = ByteArray(chunks.sumBy { it.size })
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, flattened, offset, chunk.size)
            offset += chunk.size
        }
        return ByteArrayInputStream(flattened)
    }

}

inline fun <T> Kryo.useClassLoader(cl: ClassLoader, body: () -> T): T {
    val tmp = this.classLoader ?: ClassLoader.getSystemClassLoader()
    this.classLoader = cl
    try {
        return body()
    } finally {
        this.classLoader = tmp
    }
}

fun Output.writeBytesWithLength(byteArray: ByteArray) {
    this.writeInt(byteArray.size, true)
    this.writeBytes(byteArray)
}

fun Input.readBytesWithLength(): ByteArray {
    val size = this.readInt(true)
    return this.readBytes(size)
}

/** Thrown during deserialisation to indicate that an attachment needed to construct the [WireTransaction] is not found */
@CordaSerializable
class MissingAttachmentsException(val ids: List<SecureHash>) : Exception()

/** A serialisation engine that knows how to deserialise code inside a sandbox */
@ThreadSafe
object WireTransactionSerializer : Serializer<WireTransaction>() {
    override fun write(kryo: Kryo, output: Output, obj: WireTransaction) {
        kryo.writeClassAndObject(output, obj.inputs)
        kryo.writeClassAndObject(output, obj.attachments)
        kryo.writeClassAndObject(output, obj.outputs)
        kryo.writeClassAndObject(output, obj.commands)
        kryo.writeClassAndObject(output, obj.notary)
        kryo.writeClassAndObject(output, obj.mustSign)
        kryo.writeClassAndObject(output, obj.type)
        kryo.writeClassAndObject(output, obj.timestamp)
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(kryo: Kryo, input: Input, type: Class<WireTransaction>): WireTransaction {
        val inputs = kryo.readClassAndObject(input) as List<StateRef>
        val attachmentHashes = kryo.readClassAndObject(input) as List<SecureHash>

        // If we're deserialising in the sandbox context, we use our special attachments classloader.
        // Otherwise we just assume the code we need is on the classpath already.
        val attachmentStorage = kryo.attachmentStorage
        val classLoader = if (attachmentStorage != null) {
            val missing = ArrayList<SecureHash>()
            val attachments = ArrayList<Attachment>()
            for (id in attachmentHashes) {
                val attachment = attachmentStorage.openAttachment(id)
                if (attachment == null)
                    missing += id
                else
                    attachments += attachment
            }
            if (missing.isNotEmpty())
                throw MissingAttachmentsException(missing)
            AttachmentsClassLoader(attachments)
        } else javaClass.classLoader

        kryo.useClassLoader(classLoader) {
            val outputs = kryo.readClassAndObject(input) as List<TransactionState<ContractState>>
            val commands = kryo.readClassAndObject(input) as List<Command>
            val notary = kryo.readClassAndObject(input) as Party?
            val signers = kryo.readClassAndObject(input) as List<CompositeKey>
            val transactionType = kryo.readClassAndObject(input) as TransactionType
            val timestamp = kryo.readClassAndObject(input) as Timestamp?

            return WireTransaction(inputs, attachmentHashes, outputs, commands, notary, signers, transactionType, timestamp)
        }
    }
}

/** For serialising an ed25519 private key */
@ThreadSafe
object Ed25519PrivateKeySerializer : Serializer<EdDSAPrivateKey>() {
    override fun write(kryo: Kryo, output: Output, obj: EdDSAPrivateKey) {
        check(obj.params == ed25519Curve)
        output.writeBytesWithLength(obj.seed)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<EdDSAPrivateKey>): EdDSAPrivateKey {
        val seed = input.readBytesWithLength()
        return EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, ed25519Curve))
    }
}

/** For serialising an ed25519 public key */
@ThreadSafe
object Ed25519PublicKeySerializer : Serializer<EdDSAPublicKey>() {
    override fun write(kryo: Kryo, output: Output, obj: EdDSAPublicKey) {
        check(obj.params == ed25519Curve)
        output.writeBytesWithLength(obj.abyte)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<EdDSAPublicKey>): EdDSAPublicKey {
        val A = input.readBytesWithLength()
        return EdDSAPublicKey(EdDSAPublicKeySpec(A, ed25519Curve))
    }
}

/** For serialising composite keys */
@ThreadSafe
object CompositeKeyLeafSerializer : Serializer<CompositeKey.Leaf>() {
    override fun write(kryo: Kryo, output: Output, obj: CompositeKey.Leaf) {
        val key = obj.publicKey
        kryo.writeClassAndObject(output, key)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<CompositeKey.Leaf>): CompositeKey.Leaf {
        val key = kryo.readClassAndObject(input) as PublicKey
        return CompositeKey.Leaf(key)
    }
}

@ThreadSafe
object CompositeKeyNodeSerializer : Serializer<CompositeKey.Node>() {
    override fun write(kryo: Kryo, output: Output, obj: CompositeKey.Node) {
        output.writeInt(obj.threshold)
        output.writeInt(obj.children.size)
        obj.children.forEach { kryo.writeClassAndObject(output, it) }
        output.writeInts(obj.weights.toIntArray())
    }

    override fun read(kryo: Kryo, input: Input, type: Class<CompositeKey.Node>): CompositeKey.Node {
        val threshold = input.readInt()
        val childCount = input.readInt()
        val children = (1..childCount).map { kryo.readClassAndObject(input) as CompositeKey }
        val weights = input.readInts(childCount)

        val builder = CompositeKey.Builder()
        weights.zip(children).forEach { builder.addKey(it.second, it.first)  }
        return builder.build(threshold)
    }
}

/** Marker interface for kotlin object definitions so that they are deserialized as the singleton instance. */
interface DeserializeAsKotlinObjectDef

/** Serializer to deserialize kotlin object definitions marked with [DeserializeAsKotlinObjectDef]. */
object KotlinObjectSerializer : Serializer<DeserializeAsKotlinObjectDef>() {
    override fun read(kryo: Kryo, input: Input, type: Class<DeserializeAsKotlinObjectDef>): DeserializeAsKotlinObjectDef {
        // read the public static INSTANCE field that kotlin compiler generates.
        return type.getField("INSTANCE").get(null) as DeserializeAsKotlinObjectDef
    }

    override fun write(kryo: Kryo, output: Output, obj: DeserializeAsKotlinObjectDef) {}
}

// No ClassResolver only constructor.  MapReferenceResolver is the default as used by Kryo in other constructors.
private val internalKryoPool = KryoPool.Builder { DefaultKryoCustomizer.customize(CordaKryo(makeNoWhitelistClassResolver())) }.build()
private val kryoPool = KryoPool.Builder { DefaultKryoCustomizer.customize(CordaKryo(makeStandardClassResolver())) }.build()

// No ClassResolver only constructor.  MapReferenceResolver is the default as used by Kryo in other constructors.
@VisibleForTesting
fun createTestKryo(): Kryo = DefaultKryoCustomizer.customize(CordaKryo(makeNoWhitelistClassResolver()))

/**
 * We need to disable whitelist checking during calls from our Kryo code to register a serializer, since it checks
 * for existing registrations and then will enter our [CordaClassResolver.getRegistration] method.
 */
open class CordaKryo(classResolver: ClassResolver) : Kryo(classResolver, MapReferenceResolver()) {
    override fun register(type: Class<*>?): Registration {
        (classResolver as? CordaClassResolver)?.disableWhitelist()
        try {
            return super.register(type)
        } finally {
            (classResolver as? CordaClassResolver)?.enableWhitelist()
        }
    }

    override fun register(type: Class<*>?, id: Int): Registration {
        (classResolver as? CordaClassResolver)?.disableWhitelist()
        try {
            return super.register(type, id)
        } finally {
            (classResolver as? CordaClassResolver)?.enableWhitelist()
        }
    }

    override fun register(type: Class<*>?, serializer: Serializer<*>?): Registration {
        (classResolver as? CordaClassResolver)?.disableWhitelist()
        try {
            return super.register(type, serializer)
        } finally {
            (classResolver as? CordaClassResolver)?.enableWhitelist()
        }
    }

    override fun register(registration: Registration?): Registration {
        (classResolver as? CordaClassResolver)?.disableWhitelist()
        try {
            return super.register(registration)
        } finally {
            (classResolver as? CordaClassResolver)?.enableWhitelist()
        }
    }
}

inline fun <T : Any> Kryo.register(
        type: KClass<T>,
        crossinline read: (Kryo, Input) -> T,
        crossinline write: (Kryo, Output, T) -> Unit): Registration {
    return register(
            type.java,
            object : Serializer<T>() {
                override fun read(kryo: Kryo, input: Input, type: Class<T>): T = read(kryo, input)
                override fun write(kryo: Kryo, output: Output, obj: T) = write(kryo, output, obj)
            }
    )
}

/**
 * Use this method to mark any types which can have the same instance within it more than once. This will make sure
 * the serialised form is stable across multiple serialise-deserialise cycles. Using this on a type with internal cyclic
 * references will throw a stack overflow exception during serialisation.
 */
inline fun <reified T : Any> Kryo.noReferencesWithin() {
    register(T::class.java, NoReferencesSerializer(getSerializer(T::class.java)))
}

class NoReferencesSerializer<T>(val baseSerializer: Serializer<T>) : Serializer<T>() {

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        return kryo.withoutReferences { baseSerializer.read(kryo, input, type) }
    }

    override fun write(kryo: Kryo, output: Output, obj: T) {
        kryo.withoutReferences { baseSerializer.write(kryo, output, obj) }
    }
}

fun <T> Kryo.withoutReferences(block: () -> T): T {
    val previousValue = setReferences(false)
    try {
        return block()
    } finally {
        references = previousValue
    }
}

/**
 * Improvement to the builtin JavaSerializer by honouring the [Kryo.getReferences] setting.
 */
object ReferencesAwareJavaSerializer : JavaSerializer() {
    override fun write(kryo: Kryo, output: Output, obj: Any) {
        if (kryo.references) {
            super.write(kryo, output, obj)
        } else {
            ObjectOutputStream(output).use {
                it.writeObject(obj)
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Any>): Any {
        return if (kryo.references) {
            super.read(kryo, input, type)
        } else {
            ObjectInputStream(input).use(ObjectInputStream::readObject)
        }
    }
}

val ATTACHMENT_STORAGE = "ATTACHMENT_STORAGE"

val Kryo.attachmentStorage: AttachmentStorage?
    get() = this.context.get(ATTACHMENT_STORAGE, null) as AttachmentStorage?

fun <T> Kryo.withAttachmentStorage(attachmentStorage: AttachmentStorage?, block: () -> T): T {
    val priorAttachmentStorage = this.attachmentStorage
    this.context.put(ATTACHMENT_STORAGE, attachmentStorage)
    try {
        return block()
    } finally {
        this.context.put(ATTACHMENT_STORAGE, priorAttachmentStorage)
    }
}

object OrderedSerializer : Serializer<HashMap<Any, Any>>() {
    override fun write(kryo: Kryo, output: Output, obj: HashMap<Any, Any>) {
        //Change a HashMap to LinkedHashMap.
        val linkedMap = LinkedHashMap<Any, Any>()
        val sorted = obj.toList().sortedBy { it.first.hashCode() }
        for ((k, v) in sorted) {
            linkedMap.put(k, v)
        }
        kryo.writeClassAndObject(output, linkedMap)
    }

    //It will be deserialized as a LinkedHashMap.
    @Suppress("UNCHECKED_CAST")
    override fun read(kryo: Kryo, input: Input, type: Class<HashMap<Any, Any>>): HashMap<Any, Any> {
        val hm = kryo.readClassAndObject(input) as HashMap<Any, Any>
        return hm
    }
}

/** For serialising a MetaData object. */
@ThreadSafe
object MetaDataSerializer : Serializer<MetaData>() {
    override fun write(kryo: Kryo, output: Output, obj: MetaData) {
        output.writeString(obj.schemeCodeName)
        output.writeString(obj.versionID)
        kryo.writeClassAndObject(output, obj.signatureType)
        kryo.writeClassAndObject(output, obj.timestamp)
        kryo.writeClassAndObject(output, obj.visibleInputs)
        kryo.writeClassAndObject(output, obj.signedInputs)
        output.writeBytesWithLength(obj.merkleRoot)
        output.writeBytesWithLength(obj.publicKey.encoded)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalArgumentException::class, InvalidKeySpecException::class)
    override fun read(kryo: Kryo, input: Input, type: Class<MetaData>): MetaData {
        val schemeCodeName = input.readString()
        val versionID = input.readString()
        val signatureType = kryo.readClassAndObject(input) as SignatureType
        val timestamp = kryo.readClassAndObject(input) as Instant?
        val visibleInputs = kryo.readClassAndObject(input) as BitSet?
        val signedInputs = kryo.readClassAndObject(input) as BitSet?
        val merkleRoot = input.readBytesWithLength()
        val publicKey = Crypto.decodePublicKey(input.readBytesWithLength(), schemeCodeName)
        return MetaData(schemeCodeName, versionID, signatureType, timestamp, visibleInputs, signedInputs, merkleRoot, publicKey)
    }
}

/** For serialising a Logger. */
@ThreadSafe
object LoggerSerializer : Serializer<Logger>() {
    override fun write(kryo: Kryo, output: Output, obj: Logger) {
        output.writeString(obj.name)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Logger>): Logger {
        return LoggerFactory.getLogger(input.readString())
    }
}
