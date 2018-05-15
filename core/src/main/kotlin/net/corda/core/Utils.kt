@file:JvmName("Utils")

package net.corda.core

import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.messaging.DataFeed
import rx.Observable
import rx.Observer
import java.lang.reflect.Field

// TODO Delete this file once the Future stuff is out of here

fun <A> CordaFuture<out A>.toObservable(): Observable<A> {
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
 * Returns a [CordaFuture] bound to the *first* item emitted by this Observable. The future will complete with a
 * NoSuchElementException if no items are emitted or any other error thrown by the Observable. If it's cancelled then
 * it will unsubscribe from the observable.
 */
fun <T> Observable<T>.toFuture(): CordaFuture<T> = openFuture<T>().also {
    val subscription = first().subscribe(object : Observer<T> {
        override fun onNext(value: T) {
            it.set(value)
        }

        override fun onError(e: Throwable) {
            it.setException(e)
        }

        override fun onCompleted() {}
    })
    it.then {
        if (it.isCancelled) {
            subscription.unsubscribe()
        }
    }
}

/**
 * Returns a [DataFeed] that transforms errors according to the provided [transform] function.
 */
fun <SNAPSHOT, ELEMENT> DataFeed<SNAPSHOT, ELEMENT>.mapErrors(transform: (Throwable) -> Throwable): DataFeed<SNAPSHOT, ELEMENT> {

    return copy(updates = updates.mapErrors(transform))
}

/**
 * Returns a [DataFeed] that processes errors according to the provided [action].
 */
fun <SNAPSHOT, ELEMENT> DataFeed<SNAPSHOT, ELEMENT>.doOnError(action: (Throwable) -> Unit): DataFeed<SNAPSHOT, ELEMENT> {

    return copy(updates = updates.doOnError(action))
}

/**
 * Returns an [Observable] that transforms errors according to the provided [transform] function.
 */
fun <ELEMENT> Observable<ELEMENT>.mapErrors(transform: (Throwable) -> Throwable): Observable<ELEMENT> {

    return onErrorResumeNext { error ->
        Observable.error(transform(error))
    }
}

/**
 * Find a field withing a class hierarchy.
 */
@Throws(NoSuchFieldException::class)
fun findField(fieldName: String, clazz: Class<*>?): Field {
    if (clazz == null) {
        throw NoSuchFieldException(fieldName)
    }
    return try {
        return clazz.getDeclaredField(fieldName)
    } catch (e: NoSuchFieldException) {
        findField(fieldName, clazz.superclass)
    }
}

/**
 * Sets a value for the specified field.
 */
@Throws(NoSuchFieldException::class)
fun setFieldValue(fieldName: String, value: Any?, target: Any) {

    val field = findField(fieldName, target::class.java)
    val accessible = field.isAccessible
    field.isAccessible = true
    try {
        field.set(target, value)
    } finally {
        field.isAccessible = accessible
    }
}

/**
 * Sets a value for the specified field.
 */
@Throws(NoSuchFieldException::class)
fun <TYPE : Any> TYPE.setFieldValue(fieldName: String, value: Any?) {
    setFieldValue(fieldName, value, this)
}

/**
 * Sets the specified field value to null.
 */
@Throws(NoSuchFieldException::class)
fun <TYPE : Any> TYPE.setFieldToNull(fieldName: String) {
    setFieldValue(fieldName, null, this)
}