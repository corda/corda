package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.SerializerFactory
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import net.corda.core.contracts.PrivacySalt
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.internal.LazyMappedList
import net.corda.core.internal.fullyQualifiedPackage
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializeAsTokenContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.ContractUpgradeFilteredTransaction
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.serialization.internal.serializationContextKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe
import kotlin.reflect.KClass

/**
 * Serialization utilities, using the Kryo framework with a custom serializer for immutable data classes and a dead
 * simple, totally non-extensible binary (sub)format. Used exclusively within Corda for checkpointing flows as
 * it will happily deserialise literally anything, including malicious streams that would reconstruct classes
 * in invalid states and thus violating system invariants. In the context of checkpointing a Java stack, this is
 * absolutely the functionality we desire, for a stable binary wire format and persistence technology, we have
 * the AMQP implementation.
 */

/**
 * A serializer that avoids writing the wrapper class to the byte stream, thus ensuring [SerializedBytes] is a pure
 * type safety hack.
 */
object SerializedBytesSerializer : Serializer<SerializedBytes<Any>>() {
    override fun write(kryo: Kryo, output: Output, obj: SerializedBytes<Any>) {
        output.writeVarInt(obj.size, true)
        obj.writeTo(output)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out SerializedBytes<Any>>): SerializedBytes<Any> {
        return SerializedBytes(input.readBytes(input.readVarInt(true)))
    }
}

// TODO This is a temporary inefficient serializer for sending InputStreams through RPC. This may be done much more
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
                output.writeInt(0, true)
                break
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out InputStream>): InputStream {
        val chunks = ArrayList<ByteArray>()
        while (true) {
            val chunk = input.readBytesWithLength()
            if (chunk.isEmpty()) {
                break
            } else {
                chunks.add(chunk)
            }
        }
        val flattened = ByteArray(chunks.sumOf { it.size })
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, flattened, offset, chunk.size)
            offset += chunk.size
        }
        return flattened.inputStream()
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

/** A serialisation engine that knows how to deserialise code inside a sandbox */
@ThreadSafe
object WireTransactionSerializer : Serializer<WireTransaction>() {
    override fun write(kryo: Kryo, output: Output, obj: WireTransaction) {
        kryo.writeClassAndObject(output, obj.componentGroups)
        kryo.writeClassAndObject(output, obj.privacySalt)
        kryo.writeClassAndObject(output, obj.digestService)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out WireTransaction>): WireTransaction {
        val componentGroups: List<ComponentGroup> = uncheckedCast(kryo.readClassAndObject(input))
        val privacySalt = kryo.readClassAndObject(input) as PrivacySalt
        val digestService = kryo.readClassAndObject(input) as? DigestService
        return WireTransaction(componentGroups, privacySalt, digestService ?: DigestService.sha2_256)
    }
}

@ThreadSafe
object NotaryChangeWireTransactionSerializer : Serializer<NotaryChangeWireTransaction>() {
    override fun write(kryo: Kryo, output: Output, obj: NotaryChangeWireTransaction) {
        kryo.writeClassAndObject(output, obj.serializedComponents)
        kryo.writeClassAndObject(output, obj.digestService)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out NotaryChangeWireTransaction>): NotaryChangeWireTransaction {
        val components: List<OpaqueBytes> = uncheckedCast(kryo.readClassAndObject(input))
        val digestService = kryo.readClassAndObject(input) as? DigestService
        return NotaryChangeWireTransaction(components, digestService ?: DigestService.sha2_256)
    }
}

@ThreadSafe
object ContractUpgradeWireTransactionSerializer : Serializer<ContractUpgradeWireTransaction>() {
    override fun write(kryo: Kryo, output: Output, obj: ContractUpgradeWireTransaction) {
        kryo.writeClassAndObject(output, obj.serializedComponents)
        kryo.writeClassAndObject(output, obj.privacySalt)
        kryo.writeClassAndObject(output, obj.digestService)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out ContractUpgradeWireTransaction>): ContractUpgradeWireTransaction {
        val components: List<OpaqueBytes> = uncheckedCast(kryo.readClassAndObject(input))
        val privacySalt = kryo.readClassAndObject(input) as PrivacySalt
        val digestService = kryo.readClassAndObject(input) as? DigestService
        return ContractUpgradeWireTransaction(components, privacySalt, digestService ?: DigestService.sha2_256)
    }
}

@ThreadSafe
object ContractUpgradeFilteredTransactionSerializer : Serializer<ContractUpgradeFilteredTransaction>() {
    override fun write(kryo: Kryo, output: Output, obj: ContractUpgradeFilteredTransaction) {
        kryo.writeClassAndObject(output, obj.visibleComponents)
        kryo.writeClassAndObject(output, obj.hiddenComponents)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out ContractUpgradeFilteredTransaction>): ContractUpgradeFilteredTransaction {
        val visibleComponents: Map<Int, ContractUpgradeFilteredTransaction.FilteredComponent> = uncheckedCast(kryo.readClassAndObject(input))
        val hiddenComponents: Map<Int, SecureHash> = uncheckedCast(kryo.readClassAndObject(input))
        return ContractUpgradeFilteredTransaction(visibleComponents, hiddenComponents)
    }
}

@ThreadSafe
object SignedTransactionSerializer : Serializer<SignedTransaction>() {
    override fun write(kryo: Kryo, output: Output, obj: SignedTransaction) {
        kryo.writeClassAndObject(output, obj.txBits)
        kryo.writeClassAndObject(output, obj.sigs)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out SignedTransaction>): SignedTransaction {
        return SignedTransaction(
                uncheckedCast<Any?, SerializedBytes<CoreTransaction>>(kryo.readClassAndObject(input)),
                uncheckedCast<Any?, List<TransactionSignature>>(kryo.readClassAndObject(input))
        )
    }
}

@ThreadSafe
object PrivateKeySerializer : Serializer<PrivateKey>() {
    override fun write(kryo: Kryo, output: Output, obj: PrivateKey) {
        output.writeBytesWithLength(obj.encoded)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out PrivateKey>): PrivateKey {
        val encodedKey = input.readBytesWithLength()
        return Crypto.decodePrivateKey(encodedKey)
    }
}

/** For serialising a public key */
@ThreadSafe
object PublicKeySerializer : Serializer<PublicKey>() {
    override fun write(kryo: Kryo, output: Output, obj: PublicKey) {
        // TODO: Instead of encoding to the default X509 format, we could have a custom per key type (space-efficient) serialiser.
        output.writeBytesWithLength(Crypto.encodePublicKey(obj))
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out PublicKey>): PublicKey {
        val encodedKey = input.readBytesWithLength()
        return Crypto.decodePublicKey(encodedKey)
    }
}

inline fun <T : Any> Kryo.register(
        type: KClass<T>,
        crossinline read: (Kryo, Input) -> T,
        crossinline write: (Kryo, Output, T) -> Unit): Registration {
    return register(
            type.java,
            object : Serializer<T>() {
                override fun read(kryo: Kryo, input: Input, clazz: Class<out T>): T = read(kryo, input)
                override fun write(kryo: Kryo, output: Output, obj: T) = write(kryo, output, obj)
            }
    )
}

internal val Class<*>.isPackageOpen: Boolean get() = module.isOpen(packageName, KryoCheckpointSerializer::class.java.module)

/**
 *
 */
fun Kryo.registerIfPackageOpen(type: Class<*>, createSerializer: () -> Serializer<*>, fallbackWrite: Boolean = true) {
    val serializer = if (type.isPackageOpen) createSerializer() else serializerForInaccesible(type, fallbackWrite)
    register(type, serializer)
}

/**
 *
 */
fun Kryo.registerIfPackageOpen(type: Class<*>, fallbackWrite: Boolean = true) {
    if (type.isPackageOpen) {
        register(type)
    } else {
        registerAsInaccessible(type, fallbackWrite)
    }
}

/**
 *
 */
fun Kryo.registerAsInaccessible(type: Class<*>, fallbackWrite: Boolean = true) {
    register(type, serializerForInaccesible(type, fallbackWrite))
}

private fun Kryo.serializerForInaccesible(type: Class<*>, fallbackWrite: Boolean = true): Serializer<*> {
    // Find the most specific serializer already registered to use for writing. This will be useful to make sure as much of the object
    // graph is serialised and covered in the writing phase.
    return InaccessibleSerializer<Any>(if (fallbackWrite) getSerializer(type) else null)
}


private class InaccessibleSerializer<T : Any>(private val fallbackWrite: Serializer<T>? = null) : Serializer<T>() {
    companion object {
        private val logger = contextLogger()
        private val typesLogged = Collections.newSetFromMap<Class<*>>(ConcurrentHashMap())
    }

    override fun write(kryo: Kryo, output: Output, obj: T) {
        val type = obj.javaClass
        if (typesLogged.add(type)) {
            logger.warn("${type.fullyQualifiedPackage} is not open to this test environment and so ${type.name} objects are not " +
                    "supported in checkpoints. This will most likely not be an issue unless checkpoints are restored.")
        }
        fallbackWrite?.write(kryo, output, obj)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out T>): T {
        throw UnsupportedOperationException("Restoring checkpoints containing ${type.name} objects is not supported in this test " +
                "environment. If you wish to restore these checkpoints in your tests then use the out-of-process node driver, or add " +
                "--add-opens=${type.fullyQualifiedPackage}=ALL-UNNAMED to the test JVM args.")
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

/** For serialising a Logger. */
@ThreadSafe
object LoggerSerializer : Serializer<Logger>() {
    override fun write(kryo: Kryo, output: Output, obj: Logger) {
        output.writeString(obj.name)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out Logger>): Logger {
        return LoggerFactory.getLogger(input.readString())
    }
}

object ClassSerializer : Serializer<Class<*>>() {
    override fun read(kryo: Kryo, input: Input, type: Class<out Class<*>>): Class<*> {
        val className = input.readString()
        return if (className == "void") Void.TYPE else Class.forName(className, true, kryo.classLoader)
    }

    override fun write(kryo: Kryo, output: Output, clazz: Class<*>) {
        output.writeString(clazz.name)
    }
}

@ThreadSafe
object CertPathSerializer : Serializer<CertPath>() {
    override fun read(kryo: Kryo, input: Input, type: Class<out CertPath>): CertPath {
        val factory = CertificateFactory.getInstance(input.readString())
        return factory.generateCertPath(input.readBytesWithLength().inputStream())
    }

    override fun write(kryo: Kryo, output: Output, obj: CertPath) {
        output.writeString(obj.type)
        output.writeBytesWithLength(obj.encoded)
    }
}

@ThreadSafe
object X509CertificateSerializer : Serializer<X509Certificate>() {
    override fun read(kryo: Kryo, input: Input, type: Class<out X509Certificate>): X509Certificate {
        return CertificateFactory.getInstance("X.509").generateCertificate(input.readBytesWithLength().inputStream()) as X509Certificate
    }

    override fun write(kryo: Kryo, output: Output, obj: X509Certificate) {
        output.writeBytesWithLength(obj.encoded)
    }
}

fun Kryo.serializationContext(): SerializeAsTokenContext? = context.get<SerializeAsTokenContext>(serializationContextKey) as? SerializeAsTokenContext

/**
 * For serializing instances if [Throwable] honoring the fact that [java.lang.Throwable.suppressedExceptions]
 * might be un-initialized/empty.
 * In the absence of this class [CompatibleFieldSerializer] will be used which will assign a *new* instance of
 * unmodifiable collection to [java.lang.Throwable.suppressedExceptions] which will fail some sentinel identity checks
 * e.g. in [java.lang.Throwable.addSuppressed]
 */
@ThreadSafe
class ThrowableSerializer<T>(kryo: Kryo, type: Class<T>) : Serializer<Throwable>(false, true) {

    private companion object {
        private val suppressedField = Throwable::class.java.getDeclaredField("suppressedExceptions")

        private val sentinelValue = let {
            val sentinelField = Throwable::class.java.getDeclaredField("SUPPRESSED_SENTINEL")
            sentinelField.isAccessible = true
            sentinelField.get(null)
        }

        init {
            suppressedField.isAccessible = true
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val delegate: Serializer<Throwable> = SerializerFactory.ReflectionSerializerFactory.newSerializer(kryo, FieldSerializer::class.java, type) as Serializer<Throwable>

    override fun write(kryo: Kryo, output: Output, throwable: Throwable) {
        delegate.write(kryo, output, throwable)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out Throwable>): Throwable {
        val throwableRead = delegate.read(kryo, input, type)
        if (throwableRead.suppressed.isEmpty()) {
            throwableRead.setSuppressedToSentinel()
        }
        return throwableRead
    }

    private fun Throwable.setSuppressedToSentinel() = suppressedField.set(this, sentinelValue)
}

/** For serializing the utility [LazyMappedList]. It will serialize the fully resolved object.*/
@ThreadSafe
object LazyMappedListSerializer : Serializer<List<*>>() {
    // Using a MutableList so that Kryo will always write an instance of java.util.ArrayList.
    override fun write(kryo: Kryo, output: Output, obj: List<*>) = kryo.writeClassAndObject(output, obj.toMutableList())
    override fun read(kryo: Kryo, input: Input, type: Class<out List<*>>) = kryo.readClassAndObject(input) as? List<*>
}

object KeyPairSerializer : Serializer<KeyPair>() {
    override fun write(kryo: Kryo, output: Output, obj: KeyPair) {
        kryo.writeObject(output, obj.public)
        kryo.writeObject(output, obj.private)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out KeyPair>): KeyPair {
        return KeyPair(
                kryo.readObject(input, PublicKey::class.java),
                kryo.readObject(input, PrivateKey::class.java)
        )
    }
}
