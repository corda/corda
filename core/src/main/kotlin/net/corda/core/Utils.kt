// TODO Move out the Kotlin specific stuff into a separate file
@file:JvmName("Utils")

package net.corda.core

import com.google.common.base.Function
import com.google.common.base.Throwables
import com.google.common.io.ByteStreams
import com.google.common.util.concurrent.*
import kotlinx.support.jdk7.use
import net.corda.core.crypto.newSecureRandom
import org.slf4j.Logger
import rx.Observable
import rx.Observer
import rx.subjects.PublishSubject
import rx.subjects.UnicastSubject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.*
import java.nio.file.attribute.FileAttribute
import java.time.Duration
import java.time.temporal.Temporal
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiConsumer
import java.util.stream.Stream
import java.util.zip.ZipInputStream
import kotlin.concurrent.withLock
import kotlin.reflect.KProperty

val Int.days: Duration get() = Duration.ofDays(this.toLong())
@Suppress("unused") // It's here for completeness
val Int.hours: Duration get() = Duration.ofHours(this.toLong())
val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())
val Int.millis: Duration get() = Duration.ofMillis(this.toLong())


// TODO: Review by EOY2016 if we ever found these utilities helpful.
val Int.bd: BigDecimal get() = BigDecimal(this)
val Double.bd: BigDecimal get() = BigDecimal(this)
val String.bd: BigDecimal get() = BigDecimal(this)
val Long.bd: BigDecimal get() = BigDecimal(this)

fun String.abbreviate(maxWidth: Int): String = if (length <= maxWidth) this else take(maxWidth - 1) + "…"

/** Like the + operator but throws an exception in case of integer overflow. */
infix fun Int.checkedAdd(b: Int) = Math.addExact(this, b)

/** Like the + operator but throws an exception in case of integer overflow. */
@Suppress("unused")
infix fun Long.checkedAdd(b: Long) = Math.addExact(this, b)

/**
 * Returns a random positive long generated using a secure RNG. This function sacrifies a bit of entropy in order to
 * avoid potential bugs where the value is used in a context where negative numbers are not expected.
 */
fun random63BitValue(): Long = Math.abs(newSecureRandom().nextLong())

/** Same as [Future.get] but with a more descriptive name, and doesn't throw [ExecutionException], instead throwing its cause */
fun <T> Future<T>.getOrThrow(timeout: Duration? = null): T {
    return try {
        if (timeout == null) get() else get(timeout.toNanos(), TimeUnit.NANOSECONDS)
    } catch (e: ExecutionException) {
        throw e.cause!!
    }
}

fun <T> future(block: () -> T): ListenableFuture<T> = CompletableToListenable(CompletableFuture.supplyAsync(block))

private class CompletableToListenable<T>(private val base: CompletableFuture<T>) : Future<T> by base, ListenableFuture<T> {
    override fun addListener(listener: Runnable, executor: Executor) {
        base.whenCompleteAsync(BiConsumer { result, exception -> listener.run() }, executor)
    }
}

// Some utilities for working with Guava listenable futures.
fun <T> ListenableFuture<T>.then(executor: Executor, body: () -> Unit) = addListener(Runnable(body), executor)

fun <T> ListenableFuture<T>.success(executor: Executor, body: (T) -> Unit) = then(executor) {
    val r = try {
        get()
    } catch(e: Throwable) {
        return@then
    }
    body(r)
}

fun <T> ListenableFuture<T>.failure(executor: Executor, body: (Throwable) -> Unit) = then(executor) {
    try {
        getOrThrow()
    } catch (t: Throwable) {
        body(t)
    }
}

infix fun <T> ListenableFuture<T>.then(body: () -> Unit): ListenableFuture<T> = apply { then(RunOnCallerThread, body) }
infix fun <T> ListenableFuture<T>.success(body: (T) -> Unit): ListenableFuture<T> = apply { success(RunOnCallerThread, body) }
infix fun <T> ListenableFuture<T>.failure(body: (Throwable) -> Unit): ListenableFuture<T> = apply { failure(RunOnCallerThread, body) }
infix fun <F, T> ListenableFuture<F>.map(mapper: (F) -> T): ListenableFuture<T> = Futures.transform(this, Function { mapper(it!!) })
infix fun <F, T> ListenableFuture<F>.flatMap(mapper: (F) -> ListenableFuture<T>): ListenableFuture<T> = Futures.transformAsync(this) { mapper(it!!) }
/** Executes the given block and sets the future to either the result, or any exception that was thrown. */
inline fun <T> SettableFuture<T>.catch(block: () -> T) {
    try {
        set(block())
    } catch (t: Throwable) {
        setException(t)
    }
}

fun <A> ListenableFuture<out A>.toObservable(): Observable<A> {
    return Observable.create { subscriber ->
        success {
            subscriber.onNext(it)
            subscriber.onCompleted()
        } failure {
            subscriber.onError(it)
        }
    }
}

/** Allows you to write code like: Paths.get("someDir") / "subdir" / "filename" but using the Paths API to avoid platform separator problems. */
operator fun Path.div(other: String): Path = resolve(other)

fun Path.createDirectory(vararg attrs: FileAttribute<*>): Path = Files.createDirectory(this, *attrs)
fun Path.createDirectories(vararg attrs: FileAttribute<*>): Path = Files.createDirectories(this, *attrs)
fun Path.exists(vararg options: LinkOption): Boolean = Files.exists(this, *options)
fun Path.moveTo(target: Path, vararg options: CopyOption): Path = Files.move(this, target, *options)
fun Path.isRegularFile(vararg options: LinkOption): Boolean = Files.isRegularFile(this, *options)
fun Path.isDirectory(vararg options: LinkOption): Boolean = Files.isDirectory(this, *options)
val Path.size: Long get() = Files.size(this)
inline fun <R> Path.list(block: (Stream<Path>) -> R): R = Files.list(this).use(block)
fun Path.deleteIfExists(): Boolean = Files.deleteIfExists(this)
fun Path.readAll(): ByteArray = Files.readAllBytes(this)
inline fun <R> Path.read(vararg options: OpenOption, block: (InputStream) -> R): R = Files.newInputStream(this, *options).use(block)
inline fun Path.write(createDirs: Boolean = false, vararg options: OpenOption = emptyArray(), block: (OutputStream) -> Unit) {
    if (createDirs) {
        normalize().parent?.createDirectories()
    }
    Files.newOutputStream(this, *options).use(block)
}

inline fun <R> Path.readLines(charset: Charset = UTF_8, block: (Stream<String>) -> R): R = Files.lines(this, charset).use(block)
fun Path.writeLines(lines: Iterable<CharSequence>, charset: Charset = UTF_8, vararg options: OpenOption): Path = Files.write(this, lines, charset, *options)

fun InputStream.copyTo(target: Path, vararg options: CopyOption): Long = Files.copy(this, target, *options)

// Simple infix function to add back null safety that the JDK lacks:  timeA until timeB
infix fun Temporal.until(endExclusive: Temporal) = Duration.between(this, endExclusive)

/** Returns the index of the given item or throws [IllegalArgumentException] if not found. */
fun <T> List<T>.indexOfOrThrow(item: T): Int {
    val i = indexOf(item)
    require(i != -1)
    return i
}

/**
 * Returns the single element matching the given [predicate], or `null` if element was not found,
 * or throws if more than one element was found.
 */
fun <T> Iterable<T>.noneOrSingle(predicate: (T) -> Boolean): T? {
    var single: T? = null
    for (element in this) {
        if (predicate(element)) {
            if (single == null) {
                single = element
            } else throw IllegalArgumentException("Collection contains more than one matching element.")
        }
    }
    return single
}

/** Returns single element, or `null` if element was not found, or throws if more than one element was found. */
fun <T> Iterable<T>.noneOrSingle(): T? {
    var single: T? = null
    for (element in this) {
        if (single == null) {
            single = element
        } else throw IllegalArgumentException("Collection contains more than one matching element.")
    }
    return single
}

/** Returns a random element in the list, or null if empty */
fun <T> List<T>.randomOrNull(): T? {
    if (size <= 1) return firstOrNull()
    val randomIndex = (Math.random() * size).toInt()
    return get(randomIndex)
}

/** Returns a random element in the list matching the given predicate, or null if none found */
fun <T> List<T>.randomOrNull(predicate: (T) -> Boolean) = filter(predicate).randomOrNull()

// An alias that can sometimes make code clearer to read.
val RunOnCallerThread: Executor = MoreExecutors.directExecutor()

inline fun elapsedTime(block: () -> Unit): Duration {
    val start = System.nanoTime()
    block()
    val end = System.nanoTime()
    return Duration.ofNanos(end-start)
}

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

fun <T> Logger.logElapsedTime(label: String, body: () -> T): T = logElapsedTime(label, this, body)

/**
 * A threadbox is a simple utility that makes it harder to forget to take a lock before accessing some shared state.
 * Simply define a private class to hold the data that must be grouped under the same lock, and then pass the only
 * instance to the ThreadBox constructor. You can now use the [locked] method with a lambda to take the lock in a
 * way that ensures it'll be released if there's an exception.
 *
 * Note that this technique is not infallible: if you capture a reference to the fields in another lambda which then
 * gets stored and invoked later, there may still be unsafe multi-threaded access going on, so watch out for that.
 * This is just a simple guard rail that makes it harder to slip up.
 *
 * Example:
 *
 * private class MutableState { var i = 5 }
 * private val state = ThreadBox(MutableState())
 *
 * val ii = state.locked { i }
 */
class ThreadBox<out T>(val content: T, val lock: ReentrantLock = ReentrantLock()) {
    inline fun <R> locked(body: T.() -> R): R = lock.withLock { body(content) }
    inline fun <R> alreadyLocked(body: T.() -> R): R {
        check(lock.isHeldByCurrentThread, { "Expected $lock to already be locked." })
        return body(content)
    }

    fun checkNotLocked() = check(!lock.isHeldByCurrentThread)
}

/**
 * This represents a transient exception or condition that might no longer be thrown if the operation is re-run or called
 * again.
 *
 * We avoid the use of the word transient here to hopefully reduce confusion with the term in relation to (Java) serialization.
 */
abstract class RetryableException(message: String) : Exception(message)

/**
 * A simple wrapper that enables the use of Kotlin's "val x by TransientProperty { ... }" syntax. Such a property
 * will not be serialized to disk, and if it's missing (or the first time it's accessed), the initializer will be
 * used to set it up. Note that the initializer will be called with the TransientProperty object locked.
 */
class TransientProperty<out T>(private val initializer: () -> T) {
    @Transient private var v: T? = null

    @Synchronized
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (v == null)
            v = initializer()
        return v!!
    }
}

/**
 * Given a path to a zip file, extracts it to the given directory.
 */
fun extractZipFile(zipFile: Path, toDirectory: Path) {
    val normalisedDirectory = toDirectory.normalize().createDirectories()

    zipFile.read {
        val zip = ZipInputStream(BufferedInputStream(it))
        while (true) {
            val e = zip.nextEntry ?: break
            val outPath = (normalisedDirectory / e.name).normalize()

            // Security checks: we should reject a zip that contains tricksy paths that try to escape toDirectory.
            check(outPath.startsWith(normalisedDirectory)) { "ZIP contained a path that resolved incorrectly: ${e.name}" }

            if (e.isDirectory) {
                outPath.createDirectories()
                continue
            }
            outPath.write { out ->
                ByteStreams.copy(zip, out)
            }
            zip.closeEntry()
        }
    }
}

// TODO: Generic csv printing utility for clases.

val Throwable.rootCause: Throwable get() = Throwables.getRootCause(this)

/** Representation of an operation that may have thrown an error. */
data class ErrorOr<out A> private constructor(val value: A?, val error: Throwable?) {
    // The ErrorOr holds a value iff error == null
    constructor(value: A) : this(value, null)

    companion object {
        /** Runs the given lambda and wraps the result. */
        inline fun <T : Any> catch(body: () -> T): ErrorOr<T> {
            return try {
                ErrorOr(body())
            } catch (t: Throwable) {
                ErrorOr.of(t)
            }
        }

        fun of(t: Throwable) = ErrorOr(null, t)
    }

    fun <T> match(onValue: (A) -> T, onError: (Throwable) -> T): T {
        if (error == null) {
            return onValue(value as A)
        } else {
            return onError(error)
        }
    }

    fun getOrThrow(): A {
        if (error == null) {
            return value as A
        } else {
            throw error
        }
    }

    // Functor
    fun <B> map(function: (A) -> B) = ErrorOr(value?.let(function), error)

    // Applicative
    fun <B, C> combine(other: ErrorOr<B>, function: (A, B) -> C): ErrorOr<C> {
        val newError = error ?: other.error
        return ErrorOr(if (newError != null) null else function(value as A, other.value as B), newError)
    }

    // Monad
    fun <B : Any> bind(function: (A) -> ErrorOr<B>): ErrorOr<B> {
        return if (error == null) {
            function(value as A)
        } else {
            ErrorOr.of(error)
        }
    }
}

/**
 * Returns an Observable that buffers events until subscribed.
 * @see UnicastSubject
 */
fun <T> Observable<T>.bufferUntilSubscribed(): Observable<T> {
    val subject = UnicastSubject.create<T>()
    val subscription = subscribe(subject)
    return subject.doOnUnsubscribe { subscription.unsubscribe() }
}

/**
 * Copy an [Observer] to multiple other [Observer]s.
 */
fun <T> Observer<T>.tee(vararg teeTo: Observer<T>): Observer<T> {
    val subject = PublishSubject.create<T>()
    subject.subscribe(this)
    teeTo.forEach { subject.subscribe(it) }
    return subject
}

/**
 * Returns a [ListenableFuture] bound to the *first* item emitted by this Observable. The future will complete with a
 * NoSuchElementException if no items are emitted or any other error thrown by the Observable. If it's cancelled then
 * it will unsubscribe from the observable.
 */
fun <T> Observable<T>.toFuture(): ListenableFuture<T> = ObservableToFuture(this)

private class ObservableToFuture<T>(observable: Observable<T>) : AbstractFuture<T>(), Observer<T> {
    private val subscription = observable.first().subscribe(this)
    override fun onNext(value: T) {
        set(value)
    }
    override fun onError(e: Throwable) {
        setException(e)
    }
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        subscription.unsubscribe()
        return super.cancel(mayInterruptIfRunning)
    }
    override fun onCompleted() {}
}

/** Return the sum of an Iterable of [BigDecimal]s. */
fun Iterable<BigDecimal>.sum(): BigDecimal = fold(BigDecimal.ZERO) { a, b -> a + b }
