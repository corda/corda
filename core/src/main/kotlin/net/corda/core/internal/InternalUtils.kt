@file:JvmName("InternalUtils")

package net.corda.core.internal

import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappContext
import net.corda.core.crypto.*
import net.corda.core.flows.NotarisationRequest
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
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
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.*
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileTime
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
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
fun Throwable.getStackTraceAsString() = StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()

infix fun Temporal.until(endExclusive: Temporal): Duration = Duration.between(this, endExclusive)

operator fun Duration.div(divider: Long): Duration = dividedBy(divider)
operator fun Duration.times(multiplicand: Long): Duration = multipliedBy(multiplicand)

/**
 * Allows you to write code like: Paths.get("someDir") / "subdir" / "filename" but using the Paths API to avoid platform
 * separator problems.
 */
operator fun Path.div(other: String): Path = resolve(other)

operator fun String.div(other: String): Path = Paths.get(this) / other

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

fun Path.createDirectory(vararg attrs: FileAttribute<*>): Path = Files.createDirectory(this, *attrs)
fun Path.createDirectories(vararg attrs: FileAttribute<*>): Path = Files.createDirectories(this, *attrs)
fun Path.exists(vararg options: LinkOption): Boolean = Files.exists(this, *options)
fun Path.copyToDirectory(targetDir: Path, vararg options: CopyOption): Path {
    require(targetDir.isDirectory()) { "$targetDir is not a directory" }
    val targetFile = targetDir.resolve(fileName)
    Files.copy(this, targetFile, *options)
    return targetFile
}

fun Path.moveTo(target: Path, vararg options: CopyOption): Path = Files.move(this, target, *options)
fun Path.isRegularFile(vararg options: LinkOption): Boolean = Files.isRegularFile(this, *options)
fun Path.isDirectory(vararg options: LinkOption): Boolean = Files.isDirectory(this, *options)
inline val Path.size: Long get() = Files.size(this)
fun Path.lastModifiedTime(vararg options: LinkOption): FileTime = Files.getLastModifiedTime(this, *options)
inline fun <R> Path.list(block: (Stream<Path>) -> R): R = Files.list(this).use(block)
fun Path.deleteIfExists(): Boolean = Files.deleteIfExists(this)
fun Path.reader(charset: Charset = UTF_8): BufferedReader = Files.newBufferedReader(this, charset)
fun Path.writer(charset: Charset = UTF_8, vararg options: OpenOption): BufferedWriter = Files.newBufferedWriter(this, charset, *options)
fun Path.readAll(): ByteArray = Files.readAllBytes(this)
inline fun <R> Path.read(vararg options: OpenOption, block: (InputStream) -> R): R = Files.newInputStream(this, *options).use(block)
inline fun Path.write(createDirs: Boolean = false, vararg options: OpenOption = emptyArray(), block: (OutputStream) -> Unit) {
    if (createDirs) {
        normalize().parent?.createDirectories()
    }
    Files.newOutputStream(this, *options).use(block)
}

inline fun <R> Path.readLines(charset: Charset = UTF_8, block: (Stream<String>) -> R): R = Files.lines(this, charset).use(block)
fun Path.readAllLines(charset: Charset = UTF_8): List<String> = Files.readAllLines(this, charset)
fun Path.writeLines(lines: Iterable<CharSequence>, charset: Charset = UTF_8, vararg options: OpenOption): Path = Files.write(this, lines, charset, *options)

inline fun <reified T : Any> Path.readObject(): T = readAll().deserialize()

/** Calculate the hash of the contents of this file. */
val Path.hash: SecureHash get() = read { it.hash() }

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
    try {
        return body()
    } finally {
        val elapsed = Duration.ofNanos(System.nanoTime() - now).toMillis()
        if (logger != null)
            logger.info("$label took $elapsed msec")
        else
            println("$label took $elapsed msec")
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

/** creates a new instance if not a Kotlin object */
fun <T : Any> KClass<T>.objectOrNewInstance(): T {
    return this.objectInstance ?: this.createInstance()
}

/**
 * A simple wrapper around a [Field] object providing type safe read and write access using [value], ignoring the field's
 * visibility.
 */
class DeclaredField<T>(clazz: Class<*>, name: String, private val receiver: Any?) {
    private val javaField = clazz.getDeclaredField(name).apply { isAccessible = true }
    var value: T
        get() = uncheckedCast<Any?, T>(javaField.get(receiver))
        set(value) = javaField.set(receiver, value)
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

fun URL.openHttpConnection(): HttpURLConnection = openConnection() as HttpURLConnection

fun URL.post(serializedData: OpaqueBytes): ByteArray {
    return openHttpConnection().run {
        doOutput = true
        requestMethod = "POST"
        setRequestProperty("Content-Type", "application/octet-stream")
        outputStream.use { serializedData.open().copyTo(it) }
        checkOkResponse()
        inputStream.readFully()
    }
}

fun HttpURLConnection.checkOkResponse() {
    if (responseCode != 200) {
        val message = errorStream.use { it.reader().readText() }
        throw IOException("Response Code $responseCode: $message")
    }
}

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

/** Verifies that the correct notarisation request was signed by the counterparty. */
fun NotaryFlow.Service.validateRequestSignature(request: NotarisationRequest, signature: NotarisationRequestSignature) {
    val requestingParty = otherSideSession.counterparty
    request.verifySignature(signature, requestingParty)
}

/** Creates a signature over the notarisation request using the legal identity key. */
fun NotarisationRequest.generateSignature(serviceHub: ServiceHub): NotarisationRequestSignature {
    val serializedRequest = this.serialize().bytes
    val signature = with(serviceHub) {
        val myLegalIdentity = myInfo.legalIdentitiesAndCerts.first().owningKey
        keyManagementService.sign(serializedRequest, myLegalIdentity)
    }
    return NotarisationRequestSignature(signature, serviceHub.myInfo.platformVersion)
}

val PublicKey.hash: SecureHash get() = encoded.sha256()
