@file:JvmName("InternalUtils")

package net.corda.core.internal

import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappContext
import net.corda.core.crypto.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.slf4j.Logger
import rx.Observable
import rx.Observer
import rx.subjects.PublishSubject
import rx.subjects.UnicastSubject
import java.io.*
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.*
import java.time.Duration
import java.time.temporal.Temporal
import java.util.*
import java.util.Spliterator.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream
import java.util.stream.Stream
import java.util.stream.StreamSupport
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

val Throwable.rootCause: Throwable get() = cause?.rootCause ?: this

infix fun Temporal.until(endExclusive: Temporal): Duration = Duration.between(this, endExclusive)

operator fun Duration.div(divider: Long): Duration = dividedBy(divider)
operator fun Duration.times(multiplicand: Long): Duration = multipliedBy(multiplicand)

/**
 * Returns the single element matching the given [predicate], or `null` if the collection is empty, or throws exception
 * if more than one element was found.
 */
inline fun <T> Iterable<T>.noneOrSingle(predicate: (T) -> Boolean): T? {
    val iterator = iterator()
    for (single in iterator) {
        if (predicate(single)) {
            while (iterator.hasNext()) {
                if (predicate(iterator.next())) throw IllegalArgumentException("Collection contains more than one matching element.")
            }
            return single
        }
    }
    return null
}

/**
 * Returns the single element, or `null` if the list is empty, or throws an exception if it has more than one element.
 */
fun <T> List<T>.noneOrSingle(): T? {
    return when (size) {
        0 -> null
        1 -> this[0]
        else -> throw IllegalArgumentException("List has more than one element.")
    }
}

/** Returns a random element in the list, or `null` if empty */
fun <T> List<T>.randomOrNull(): T? {
    return when (size) {
        0 -> null
        1 -> this[0]
        else -> this[(Math.random() * size).toInt()]
    }
}

/** Returns the index of the given item or throws [IllegalArgumentException] if not found. */
fun <T> List<T>.indexOfOrThrow(item: T): Int {
    val i = indexOf(item)
    require(i != -1)
    return i
}

fun InputStream.copyTo(target: Path, vararg options: CopyOption): Long = Files.copy(this, target, *options)

/** Same as [InputStream.readBytes] but also closes the stream. */
fun InputStream.readFully(): ByteArray = use { it.readBytes() }

/** Calculate the hash of the remaining bytes in this input stream. The stream is closed at the end. */
fun InputStream.hash(): SecureHash {
    return use {
        val his = HashingInputStream(Hashing.sha256(), it)
        his.copyTo(NullOutputStream)  // To avoid reading in the entire stream into memory just write out the bytes to /dev/null
        SecureHash.SHA256(his.hash().asBytes())
    }
}

inline fun <reified T : Any> InputStream.readObject(): T = readFully().deserialize()

object NullOutputStream : OutputStream() {
    override fun write(b: Int) = Unit
    override fun write(b: ByteArray) = Unit
    override fun write(b: ByteArray, off: Int, len: Int) = Unit
}

fun String.abbreviate(maxWidth: Int): String = if (length <= maxWidth) this else take(maxWidth - 1) + "…"

/** Return the sum of an Iterable of [BigDecimal]s. */
fun Iterable<BigDecimal>.sum(): BigDecimal = fold(BigDecimal.ZERO) { a, b -> a + b }

/**
 * Returns an Observable that buffers events until subscribed.
 * @see UnicastSubject
 */
fun <T> Observable<T>.bufferUntilSubscribed(): Observable<T> {
    val subject = UnicastSubject.create<T>()
    val subscription = subscribe(subject)
    return subject.doOnUnsubscribe { subscription.unsubscribe() }
}

/** Copy an [Observer] to multiple other [Observer]s. */
fun <T> Observer<T>.tee(vararg teeTo: Observer<T>): Observer<T> {
    val subject = PublishSubject.create<T>()
    subject.subscribe(this)
    teeTo.forEach { subject.subscribe(it) }
    return subject
}

/** Executes the given code block and returns a [Duration] of how long it took to execute in nanosecond precision. */
inline fun elapsedTime(block: () -> Unit): Duration {
    val start = System.nanoTime()
    block()
    val end = System.nanoTime()
    return Duration.ofNanos(end - start)
}


fun <T> Logger.logElapsedTime(label: String, body: () -> T): T = logElapsedTime(label, this, body)

// TODO: Add inline back when a new Kotlin version is released and check if the java.lang.VerifyError
// returns in the IRSSimulationTest. If not, commit the inline back.
fun <T> logElapsedTime(label: String, logger: Logger? = null, body: () -> T): T {
    // Use nanoTime as it's monotonic.
    val now = System.nanoTime()
    var failed = false
    try {
        return body()
    }
    catch (th: Throwable) {
        failed = true
        throw th
    }
    finally {
        val elapsed = Duration.ofNanos(System.nanoTime() - now).toMillis()
        val msg = (if(failed) "Failed " else "") + "$label took $elapsed msec"
        if (logger != null)
            logger.info(msg)
        else
            println(msg)
    }
}

/** Convert a [ByteArrayOutputStream] to [InputStreamAndHash]. */
fun ByteArrayOutputStream.toInputStreamAndHash(): InputStreamAndHash {
    val bytes = toByteArray()
    return InputStreamAndHash(bytes.inputStream(), bytes.sha256())
}

data class InputStreamAndHash(val inputStream: InputStream, val sha256: SecureHash.SHA256) {
    companion object {
        /**
         * Get a valid InputStream from an in-memory zip as required for some tests. The zip consists of a single file
         * called "z" that contains the given content byte repeated the given number of times.
         * Note that a slightly bigger than numOfExpectedBytes size is expected.
         */
        fun createInMemoryTestZip(numOfExpectedBytes: Int, content: Byte): InputStreamAndHash {
            require(numOfExpectedBytes > 0)
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                val arraySize = 1024
                val bytes = ByteArray(arraySize) { content }
                val n = (numOfExpectedBytes - 1) / arraySize + 1 // same as Math.ceil(numOfExpectedBytes/arraySize).
                zos.setLevel(Deflater.NO_COMPRESSION)
                zos.putNextEntry(ZipEntry("z"))
                for (i in 0 until n) {
                    zos.write(bytes, 0, arraySize)
                }
                zos.closeEntry()
            }
            return baos.toInputStreamAndHash()
        }
    }
}

fun IntIterator.toJavaIterator(): PrimitiveIterator.OfInt {
    return object : PrimitiveIterator.OfInt {
        override fun nextInt() = this@toJavaIterator.nextInt()
        override fun hasNext() = this@toJavaIterator.hasNext()
        override fun remove() = throw UnsupportedOperationException("remove")
    }
}

private fun IntProgression.toSpliterator(): Spliterator.OfInt {
    val spliterator = Spliterators.spliterator(
            iterator().toJavaIterator(),
            (1 + (last - first) / step).toLong(),
            SUBSIZED or IMMUTABLE or NONNULL or SIZED or ORDERED or SORTED or DISTINCT
    )
    return if (step > 0) spliterator else object : Spliterator.OfInt by spliterator {
        override fun getComparator() = Comparator.reverseOrder<Int>()
    }
}

fun IntProgression.stream(parallel: Boolean = false): IntStream = StreamSupport.intStream(toSpliterator(), parallel)

// When toArray has filled in the array, the component type is no longer T? but T (that may itself be nullable):
inline fun <reified T> Stream<out T>.toTypedArray(): Array<T> = uncheckedCast(toArray { size -> arrayOfNulls<T>(size) })

inline fun <T, R : Any> Stream<T>.mapNotNull(crossinline transform: (T) -> R?): Stream<R> {
    return flatMap {
        val value = transform(it)
        if (value != null) Stream.of(value) else Stream.empty()
    }
}

fun <T> Class<T>.castIfPossible(obj: Any): T? = if (isInstance(obj)) cast(obj) else null

/** Returns a [DeclaredField] wrapper around the declared (possibly non-public) static field of the receiver [Class]. */
fun <T> Class<*>.staticField(name: String): DeclaredField<T> = DeclaredField(this, name, null)

/** Returns a [DeclaredField] wrapper around the declared (possibly non-public) static field of the receiver [KClass]. */
fun <T> KClass<*>.staticField(name: String): DeclaredField<T> = DeclaredField(java, name, null)

/** Returns a [DeclaredField] wrapper around the declared (possibly non-public) instance field of the receiver object. */
fun <T> Any.declaredField(name: String): DeclaredField<T> = DeclaredField(javaClass, name, this)

/**
 * Returns a [DeclaredField] wrapper around the (possibly non-public) instance field of the receiver object, but declared
 * in its superclass [clazz].
 */
fun <T> Any.declaredField(clazz: KClass<*>, name: String): DeclaredField<T> = DeclaredField(clazz.java, name, this)

/**
 * Returns a [DeclaredField] wrapper around the (possibly non-public) instance field of the receiver object, but declared
 * in its superclass [clazz].
 */
fun <T> Any.declaredField(clazz: Class<*>, name: String): DeclaredField<T> = DeclaredField(clazz, name, this)

/** creates a new instance if not a Kotlin object */
fun <T : Any> KClass<T>.objectOrNewInstance(): T {
    return this.objectInstance ?: this.createInstance()
}

/**
 * A simple wrapper around a [Field] object providing type safe read and write access using [value], ignoring the field's
 * visibility.
 */
class DeclaredField<T>(clazz: Class<*>, name: String, private val receiver: Any?) {
    private val javaField = findField(name, clazz)
    var value: T
        get() {
            return javaField.accessible { uncheckedCast<Any?, T>(get(receiver)) }
        }
        set(value) {
            javaField.accessible {
                set(receiver, value)
            }
        }
    val name: String = javaField.name

    private fun <RESULT> Field.accessible(action: Field.() -> RESULT): RESULT {
        val accessible = isAccessible
        isAccessible = true
        try {
            return action(this)
        } finally {
            isAccessible = accessible
        }
    }
}

@Throws(NoSuchFieldException::class)
private fun findField(fieldName: String, clazz: Class<*>?): Field {
    if (clazz == null) {
        throw NoSuchFieldException(fieldName)
    }
    return try {
        return clazz.getDeclaredField(fieldName)
    } catch (e: NoSuchFieldException) {
        findField(fieldName, clazz.superclass)
    }
}

/** The annotated object would have a more restricted visibility were it not needed in tests. */
@Target(AnnotationTarget.CLASS,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.CONSTRUCTOR,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class VisibleForTesting

@Suppress("UNCHECKED_CAST")
fun <T, U : T> uncheckedCast(obj: T) = obj as U

fun <K, V> Iterable<Pair<K, V>>.toMultiMap(): Map<K, List<V>> = this.groupBy({ it.first }) { it.second }

/** Provide access to internal method for AttachmentClassLoaderTests */
fun TransactionBuilder.toWireTransaction(services: ServicesForResolution, serializationContext: SerializationContext): WireTransaction {
    return toWireTransactionWithContext(services, serializationContext)
}

/** Provide access to internal method for AttachmentClassLoaderTests */
fun TransactionBuilder.toLedgerTransaction(services: ServicesForResolution, serializationContext: SerializationContext) = toLedgerTransactionWithContext(services, serializationContext)

/** Convenience method to get the package name of a class literal. */
val KClass<*>.packageName: String get() = java.`package`.name

inline val Class<*>.isAbstractClass: Boolean get() = Modifier.isAbstract(modifiers)

inline val Class<*>.isConcreteClass: Boolean get() = !isInterface && !isAbstractClass

fun URI.toPath(): Path = Paths.get(this)

fun URL.toPath(): Path = toURI().toPath()

fun URL.openHttpConnection(): HttpURLConnection = openConnection() as HttpURLConnection

fun URL.post(serializedData: OpaqueBytes, vararg properties: Pair<String, String>): ByteArray {
    return openHttpConnection().run {
        doOutput = true
        requestMethod = "POST"
        properties.forEach { (key, value) -> setRequestProperty(key, value) }
        setRequestProperty("Content-Type", "application/octet-stream")
        outputStream.use { serializedData.open().copyTo(it) }
        checkOkResponse()
        inputStream.readFully()
    }
}

fun HttpURLConnection.checkOkResponse() {
    if (responseCode != HTTP_OK) {
        throw IOException("Response Code $responseCode: $errorMessage")
    }
}

val HttpURLConnection.errorMessage: String? get() = errorStream?.let { it.use { it.reader().readText() } }

inline fun <reified T : Any> HttpURLConnection.responseAs(): T {
    checkOkResponse()
    return inputStream.readObject()
}

/** Analogous to [Thread.join]. */
fun ExecutorService.join() {
    shutdown() // Do not change to shutdownNow, tests use this method to assert the executor has no more tasks.
    while (!awaitTermination(1, TimeUnit.SECONDS)) {
        // Try forever. Do not give up, tests use this method to assert the executor has no more tasks.
    }
}

// TODO: Currently the certificate revocation status is not handled here. Nowhere in the code the second parameter is used. Consider adding the support in the future.
fun CertPath.validate(trustAnchor: TrustAnchor, checkRevocation: Boolean = false): PKIXCertPathValidatorResult {
    val parameters = PKIXParameters(setOf(trustAnchor)).apply { isRevocationEnabled = checkRevocation }
    try {
        return CertPathValidator.getInstance("PKIX").validate(this, parameters) as PKIXCertPathValidatorResult
    } catch (e: CertPathValidatorException) {
        throw CertPathValidatorException(
                """Cert path failed to validate.
Reason: ${e.reason}
Offending cert index: ${e.index}
Cert path: $this

Trust anchor:
$trustAnchor""", e, this, e.index)
    }
}

/**
 * Return the underlying X.500 name from this Corda-safe X.500 name. These are guaranteed to have a consistent
 * ordering, such that their `toString()` function returns the same value every time for the same [CordaX500Name].
 */
val CordaX500Name.x500Name: X500Name
    get() {
        return X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.C, country)
            state?.let { addRDN(BCStyle.ST, it) }
            addRDN(BCStyle.L, locality)
            addRDN(BCStyle.O, organisation)
            organisationUnit?.let { addRDN(BCStyle.OU, it) }
            commonName?.let { addRDN(BCStyle.CN, it) }
        }.build()
    }

@Suppress("unused")
@VisibleForTesting
val CordaX500Name.Companion.unspecifiedCountry
    get() = "ZZ"

inline fun <T : Any> T.signWithCert(signer: (SerializedBytes<T>) -> DigitalSignatureWithCert): SignedDataWithCert<T> {
    val serialised = serialize()
    return SignedDataWithCert(serialised, signer(serialised))
}

fun <T : Any> T.signWithCert(privateKey: PrivateKey, certificate: X509Certificate): SignedDataWithCert<T> {
    return signWithCert {
        val signature = Crypto.doSign(privateKey, it.bytes)
        DigitalSignatureWithCert(certificate, signature)
    }
}

inline fun <T : Any> SerializedBytes<T>.sign(signer: (SerializedBytes<T>) -> DigitalSignature.WithKey): SignedData<T> {
    return SignedData(this, signer(this))
}

fun <T : Any> SerializedBytes<T>.sign(keyPair: KeyPair): SignedData<T> = SignedData(this, keyPair.sign(this.bytes))

fun ByteBuffer.copyBytes(): ByteArray = ByteArray(remaining()).also { get(it) }

fun createCordappContext(cordapp: Cordapp, attachmentId: SecureHash?, classLoader: ClassLoader, config: CordappConfig): CordappContext {
    return CordappContext(cordapp, attachmentId, classLoader, config)
}

val PublicKey.hash: SecureHash get() = encoded.sha256()

/**
 * Extension method for providing a sumBy method that processes and returns a Long
 */
fun <T> Iterable<T>.sumByLong(selector: (T) -> Long): Long = this.map { selector(it) }.sum()
