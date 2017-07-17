package net.corda.core.serialization

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoCallback
import com.esotericsoftware.kryo.pool.KryoPool
import com.esotericsoftware.kryo.util.MapReferenceResolver
import com.google.common.annotations.VisibleForTesting
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.crypto.composite.CompositeKey
import net.corda.core.identity.Party
import net.corda.core.node.AttachmentsClassLoader
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.LazyPool
import net.corda.core.utilities.OpaqueBytes
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.spec.InvalidKeySpecException
import java.time.Instant
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
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
@Suppress("unused") // Type parameter is just for documentation purposes.
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


private val serializeBufferPool = LazyPool(
        newInstance = { ByteArray(64 * 1024) }
)
private val serializeOutputStreamPool = LazyPool(
        clear = ByteArrayOutputStream::reset,
        shouldReturnToPool = { it.size() < 256 * 1024 }, // Discard if it grew too large
        newInstance = { ByteArrayOutputStream(64 * 1024) }
)
fun <T : Any> T.serialize(kryo: Kryo, internalOnly: Boolean = false): SerializedBytes<T> {
    return serializeOutputStreamPool.run { stream ->
        serializeBufferPool.run { buffer ->
            Output(buffer).use {
                it.outputStream = stream
                it.writeBytes(KryoHeaderV0_1.bytes)
                kryo.writeClassAndObject(it, this)
            }
            SerializedBytes(stream.toByteArray(), internalOnly)
        }
    }
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
    @VisibleForTesting
    internal val attachmentsClassLoaderEnabled = "attachments.class.loader.enabled"

    override fun write(kryo: Kryo, output: Output, obj: WireTransaction) {
        kryo.writeClassAndObject(output, obj.inputs)
        kryo.writeClassAndObject(output, obj.attachments)
        kryo.writeClassAndObject(output, obj.outputs)
        kryo.writeClassAndObject(output, obj.commands)
        kryo.writeClassAndObject(output, obj.notary)
        kryo.writeClassAndObject(output, obj.mustSign)
        kryo.writeClassAndObject(output, obj.type)
        kryo.writeClassAndObject(output, obj.timeWindow)
    }

    private fun attachmentsClassLoader(kryo: Kryo, attachmentHashes: List<SecureHash>): ClassLoader? {
        kryo.context[attachmentsClassLoaderEnabled] as? Boolean ?: false || return null
        val serializationContext = kryo.serializationContext() ?: return null // Some tests don't set one.
        val missing = ArrayList<SecureHash>()
        val attachments = ArrayList<Attachment>()
        attachmentHashes.forEach { id ->
            serializationContext.serviceHub.attachments.openAttachment(id)?.let { attachments += it } ?: run { missing += id }
        }
        missing.isNotEmpty() && throw MissingAttachmentsException(missing)
        return AttachmentsClassLoader(attachments)
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(kryo: Kryo, input: Input, type: Class<WireTransaction>): WireTransaction {
        val inputs = kryo.readClassAndObject(input) as List<StateRef>
        val attachmentHashes = kryo.readClassAndObject(input) as List<SecureHash>

        // If we're deserialising in the sandbox context, we use our special attachments classloader.
        // Otherwise we just assume the code we need is on the classpath already.
        kryo.useClassLoader(attachmentsClassLoader(kryo, attachmentHashes) ?: javaClass.classLoader) {
            val outputs = kryo.readClassAndObject(input) as List<TransactionState<ContractState>>
            val commands = kryo.readClassAndObject(input) as List<Command>
            val notary = kryo.readClassAndObject(input) as Party?
            val signers = kryo.readClassAndObject(input) as List<PublicKey>
            val transactionType = kryo.readClassAndObject(input) as TransactionType
            val timeWindow = kryo.readClassAndObject(input) as TimeWindow?
            return WireTransaction(inputs, attachmentHashes, outputs, commands, notary, signers, transactionType, timeWindow)
        }
    }
}

/** For serialising an ed25519 private key */
@ThreadSafe
object Ed25519PrivateKeySerializer : Serializer<EdDSAPrivateKey>() {
    override fun write(kryo: Kryo, output: Output, obj: EdDSAPrivateKey) {
        check(obj.params == Crypto.EDDSA_ED25519_SHA512.algSpec )
        output.writeBytesWithLength(obj.seed)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<EdDSAPrivateKey>): EdDSAPrivateKey {
        val seed = input.readBytesWithLength()
        return EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, Crypto.EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec))
    }
}

/** For serialising an ed25519 public key */
@ThreadSafe
object Ed25519PublicKeySerializer : Serializer<EdDSAPublicKey>() {
    override fun write(kryo: Kryo, output: Output, obj: EdDSAPublicKey) {
        check(obj.params == Crypto.EDDSA_ED25519_SHA512.algSpec)
        output.writeBytesWithLength(obj.abyte)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<EdDSAPublicKey>): EdDSAPublicKey {
        val A = input.readBytesWithLength()
        return EdDSAPublicKey(EdDSAPublicKeySpec(A, Crypto.EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec))
    }
}

/** For serialising an ed25519 public key */
@ThreadSafe
object ECPublicKeyImplSerializer : Serializer<sun.security.ec.ECPublicKeyImpl>() {
    override fun write(kryo: Kryo, output: Output, obj: sun.security.ec.ECPublicKeyImpl) {
        output.writeBytesWithLength(obj.encoded)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<sun.security.ec.ECPublicKeyImpl>): sun.security.ec.ECPublicKeyImpl {
        val A = input.readBytesWithLength()
        val der = sun.security.util.DerValue(A)
        return sun.security.ec.ECPublicKeyImpl.parse(der) as sun.security.ec.ECPublicKeyImpl
    }
}

// TODO Implement standardized serialization of CompositeKeys. See JIRA issue: CORDA-249.
@ThreadSafe
object CompositeKeySerializer : Serializer<CompositeKey>() {
    override fun write(kryo: Kryo, output: Output, obj: CompositeKey) {
        output.writeInt(obj.threshold)
        output.writeInt(obj.children.size)
        obj.children.forEach { kryo.writeClassAndObject(output, it) }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<CompositeKey>): CompositeKey {
        val threshold = input.readInt()
        val children = readListOfLength<CompositeKey.NodeAndWeight>(kryo, input, minLen = 2)
        val builder = CompositeKey.Builder()
        children.forEach { builder.addKey(it.node, it.weight) }
        return builder.build(threshold) as CompositeKey
    }
}

@ThreadSafe
object PrivateKeySerializer : Serializer<PrivateKey>() {
    override fun write(kryo: Kryo, output: Output, obj: PrivateKey) {
        output.writeBytesWithLength(obj.encoded)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<PrivateKey>): PrivateKey {
        val A = input.readBytesWithLength()
        return Crypto.decodePrivateKey(A)
    }
}

/** For serialising a public key */
@ThreadSafe
object PublicKeySerializer : Serializer<PublicKey>() {
    override fun write(kryo: Kryo, output: Output, obj: PublicKey) {
        // TODO: Instead of encoding to the default X509 format, we could have a custom per key type (space-efficient) serialiser.
        output.writeBytesWithLength(obj.encoded)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<PublicKey>): PublicKey {
        val A = input.readBytesWithLength()
        return Crypto.decodePublicKey(A)
    }
}

/**
 * Helper function for reading lists with number of elements at the beginning.
 * @param minLen minimum number of elements we expect for list to include, defaults to 1
 * @param expectedLen expected length of the list, defaults to null if arbitrary length list read
 */
inline fun <reified T> readListOfLength(kryo: Kryo, input: Input, minLen: Int = 1, expectedLen: Int? = null): List<T> {
    val elemCount = input.readInt()
    if (elemCount < minLen) throw KryoException("Cannot deserialize list, too little elements. Minimum required: $minLen, got: $elemCount")
    if (expectedLen != null && elemCount != expectedLen)
        throw KryoException("Cannot deserialize list, expected length: $expectedLen, got: $elemCount.")
    val list = (1..elemCount).map { kryo.readClassAndObject(input) as T }
    return list
}

// No ClassResolver only constructor.  MapReferenceResolver is the default as used by Kryo in other constructors.
private val internalKryoPool = KryoPool.Builder { DefaultKryoCustomizer.customize(CordaKryo(makeAllButBlacklistedClassResolver())) }.build()
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
                override fun read(kryo: Kryo, input: Input, clazz: Class<T>): T = read(kryo, input)
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
        val publicKey = Crypto.decodePublicKey(schemeCodeName, input.readBytesWithLength())
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

object ClassSerializer : Serializer<Class<*>>() {
    override fun read(kryo: Kryo, input: Input, type: Class<Class<*>>): Class<*> {
        val className = input.readString()
        return Class.forName(className)
    }

    override fun write(kryo: Kryo, output: Output, clazz: Class<*>) {
        output.writeString(clazz.name)
    }
}

/**
 * For serialising an [X500Name] without touching Sun internal classes.
 */
@ThreadSafe
object X500NameSerializer : Serializer<X500Name>() {
    override fun read(kryo: Kryo, input: Input, type: Class<X500Name>): X500Name {
        return X500Name.getInstance(ASN1InputStream(input.readBytes()).readObject())
    }

    override fun write(kryo: Kryo, output: Output, obj: X500Name) {
        output.writeBytes(obj.encoded)
    }
}

/**
 * For serialising an [CertPath] in an X.500 standard format.
 */
@ThreadSafe
object CertPathSerializer : Serializer<CertPath>() {
    val factory: CertificateFactory = CertificateFactory.getInstance("X.509")
    override fun read(kryo: Kryo, input: Input, type: Class<CertPath>): CertPath {
        return factory.generateCertPath(input)
    }

    override fun write(kryo: Kryo, output: Output, obj: CertPath) {
        output.writeBytes(obj.encoded)
    }
}

/**
 * For serialising an [X509CertificateHolder] in an X.500 standard format.
 */
@ThreadSafe
object X509CertificateSerializer : Serializer<X509CertificateHolder>() {
    override fun read(kryo: Kryo, input: Input, type: Class<X509CertificateHolder>): X509CertificateHolder {
        return X509CertificateHolder(input.readBytes())
    }

    override fun write(kryo: Kryo, output: Output, obj: X509CertificateHolder) {
        output.writeBytes(obj.encoded)
    }
}

class KryoPoolWithContext(val baseKryoPool: KryoPool, val contextKey: Any, val context: Any) : KryoPool {
    override fun <T : Any?> run(callback: KryoCallback<T>): T {
        val kryo = borrow()
        try {
            return callback.execute(kryo)
        } finally {
            release(kryo)
        }
    }

    override fun borrow(): Kryo {
        val kryo = baseKryoPool.borrow()
        require(kryo.context.put(contextKey, context) == null) { "KryoPool already has context" }
        return kryo
    }

    override fun release(kryo: Kryo) {
        requireNotNull(kryo.context.remove(contextKey)) { "Kryo instance lost context while borrowed" }
        baseKryoPool.release(kryo)
    }
}
