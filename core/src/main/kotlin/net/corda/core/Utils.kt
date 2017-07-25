@file:JvmName("Utils")

package net.corda.core

import com.google.common.util.concurrent.*
import org.slf4j.Logger
import rx.Observable
import rx.Observer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

// TODO Delete this file once the Future stuff is out of here

/** Same as [Future.get] but with a more descriptive name, and doesn't throw [ExecutionException], instead throwing its cause */
fun <T> Future<T>.getOrThrow(timeout: Duration? = null): T {
    return try {
        if (timeout == null) get() else get(timeout.toNanos(), TimeUnit.NANOSECONDS)
    } catch (e: ExecutionException) {
        throw e.cause!!
    }
}

fun <V> future(block: () -> V): Future<V> = CompletableFuture.supplyAsync(block)

fun <F : ListenableFuture<*>, V> F.then(block: (F) -> V) = addListener(Runnable { block(this) }, MoreExecutors.directExecutor())

fun <U, V> Future<U>.match(success: (U) -> V, failure: (Throwable) -> V): V {
    return success(try {
        getOrThrow()
    } catch (t: Throwable) {
        return failure(t)
    })
}

fun <U, V, W> ListenableFuture<U>.thenMatch(success: (U) -> V, failure: (Throwable) -> W) = then { it.match(success, failure) }
fun ListenableFuture<*>.andForget(log: Logger) = then { it.match({}, { log.error("Background task failed:", it) }) }
@Suppress("UNCHECKED_CAST") // We need the awkward cast because otherwise F cannot be nullable, even though it's safe.
infix fun <F, T> ListenableFuture<F>.map(mapper: (F) -> T): ListenableFuture<T> = Futures.transform(this, { (mapper as (F?) -> T)(it) })
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
        thenMatch({
            subscriber.onNext(it)
            subscriber.onCompleted()
        }, {
            subscriber.onError(it)
        })
    }
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
