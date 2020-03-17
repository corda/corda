package net.corda.node.utilities

import com.google.common.util.concurrent.SettableFuture
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.tee
import net.corda.core.observable.internal.ResilientSubscriber
import net.corda.core.observable.internal.OnNextFailedException
import net.corda.core.observable.continueOnError
import net.corda.node.services.vault.resilientOnError
import net.corda.nodeapi.internal.persistence.*
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import rx.Observable
import rx.Subscriber
import rx.exceptions.CompositeException
import rx.exceptions.OnErrorFailedException
import rx.exceptions.OnErrorNotImplementedException
import rx.internal.util.ActionSubscriber
import rx.observers.SafeSubscriber
import rx.observers.Subscribers
import rx.subjects.PublishSubject
import java.io.Closeable
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ObservablesTests {
    private fun isInDatabaseTransaction() = contextTransactionOrNull != null
    private val toBeClosed = mutableListOf<Closeable>()

    private fun createDatabase(): CordaPersistence {
        val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null })
        toBeClosed += database
        return database
    }

    @After
    fun after() {
        toBeClosed.forEach { it.close() }
        toBeClosed.clear()
    }

    @Test(timeout=300_000)
    fun `bufferUntilDatabaseCommit delays until transaction closed`() {
        val database = createDatabase()

        val source = PublishSubject.create<Int>()
        val observable: Observable<Int> = source

        val firstEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val secondEvent = SettableFuture.create<Pair<Int, Boolean>>()

        observable.first().subscribe { firstEvent.set(it to isInDatabaseTransaction()) }
        observable.skip(1).first().subscribe { secondEvent.set(it to isInDatabaseTransaction()) }

        database.transaction {
            val delayedSubject = source.bufferUntilDatabaseCommit()
            assertThat(source).isNotEqualTo(delayedSubject)
            delayedSubject.onNext(0)
            source.onNext(1)
            assertThat(firstEvent.isDone).isTrue()
            assertThat(secondEvent.isDone).isFalse()
        }
        assertThat(secondEvent.isDone).isTrue()

        assertThat(firstEvent.get()).isEqualTo(1 to true)
        assertThat(secondEvent.get()).isEqualTo(0 to false)
    }

    class TestException : Exception("Synthetic exception for tests")

    @Test(timeout=300_000)
    fun `bufferUntilDatabaseCommit swallows if transaction rolled back`() {
        val database = createDatabase()

        val source = PublishSubject.create<Int>()
        val observable: Observable<Int> = source

        val firstEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val secondEvent = SettableFuture.create<Pair<Int, Boolean>>()

        observable.first().subscribe { firstEvent.set(it to isInDatabaseTransaction()) }
        observable.skip(1).first().subscribe { secondEvent.set(it to isInDatabaseTransaction()) }

        try {
            database.transaction {
                val delayedSubject = source.bufferUntilDatabaseCommit()
                assertThat(source).isNotEqualTo(delayedSubject)
                delayedSubject.onNext(0)
                source.onNext(1)
                assertThat(firstEvent.isDone).isTrue()
                assertThat(secondEvent.isDone).isFalse()
                throw TestException()
            }
        } catch (e: TestException) {
        }
        assertThat(secondEvent.isDone).isFalse()

        assertThat(firstEvent.get()).isEqualTo(1 to true)
    }

    @Test(timeout=300_000)
    fun `bufferUntilDatabaseCommit propagates error if transaction rolled back`() {
        val database = createDatabase()

        val source = PublishSubject.create<Int>()
        val observable: Observable<Int> = source

        val firstEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val secondEvent = SettableFuture.create<Pair<Int, Boolean>>()

        observable.first().subscribe({ firstEvent.set(it to isInDatabaseTransaction()) }, {})
        observable.skip(1).subscribe({ secondEvent.set(it to isInDatabaseTransaction()) }, {})
        observable.skip(1).subscribe({}, { secondEvent.set(2 to isInDatabaseTransaction()) })

        try {
            database.transaction {
                val delayedSubject = source.bufferUntilDatabaseCommit(propagateRollbackAsError = true)
                assertThat(source).isNotEqualTo(delayedSubject)
                delayedSubject.onNext(0)
                source.onNext(1)
                assertThat(firstEvent.isDone).isTrue()
                assertThat(secondEvent.isDone).isFalse()
                throw TestException()
            }
        } catch (e: TestException) {
        }
        assertThat(secondEvent.isDone).isTrue()

        assertThat(firstEvent.get()).isEqualTo(1 to true)
        assertThat(secondEvent.get()).isEqualTo(2 to false)
    }

    @Test(timeout=300_000)
    fun `bufferUntilDatabaseCommit delays until transaction closed repeatable`() {
        val database = createDatabase()

        val source = PublishSubject.create<Int>()
        val observable: Observable<Int> = source

        val firstEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val secondEvent = SettableFuture.create<Pair<Int, Boolean>>()

        observable.first().subscribe { firstEvent.set(it to isInDatabaseTransaction()) }
        observable.skip(1).first().subscribe { secondEvent.set(it to isInDatabaseTransaction()) }

        database.transaction {
            val delayedSubject = source.bufferUntilDatabaseCommit()
            assertThat(source).isNotEqualTo(delayedSubject)
            delayedSubject.onNext(0)
            assertThat(firstEvent.isDone).isFalse()
            assertThat(secondEvent.isDone).isFalse()
        }
        assertThat(firstEvent.isDone).isTrue()
        assertThat(firstEvent.get()).isEqualTo(0 to false)
        assertThat(secondEvent.isDone).isFalse()

        database.transaction {
            val delayedSubject = source.bufferUntilDatabaseCommit()
            assertThat(source).isNotEqualTo(delayedSubject)
            delayedSubject.onNext(1)
            assertThat(secondEvent.isDone).isFalse()
        }
        assertThat(secondEvent.isDone).isTrue()
        assertThat(secondEvent.get()).isEqualTo(1 to false)
    }

    @Test(timeout=300_000)
    fun `tee correctly copies observations to multiple observers`() {

        val source1 = PublishSubject.create<Int>()
        val source2 = PublishSubject.create<Int>()
        val source3 = PublishSubject.create<Int>()

        val event1 = SettableFuture.create<Int>()
        val event2 = SettableFuture.create<Int>()
        val event3 = SettableFuture.create<Int>()

        source1.subscribe { event1.set(it) }
        source2.subscribe { event2.set(it) }
        source3.subscribe { event3.set(it) }

        val tee = source1.tee(source2, source3)
        tee.onNext(0)

        assertThat(event1.isDone).isTrue()
        assertThat(event2.isDone).isTrue()
        assertThat(event3.isDone).isTrue()
        assertThat(event1.get()).isEqualTo(0)
        assertThat(event2.get()).isEqualTo(0)
        assertThat(event3.get()).isEqualTo(0)

        tee.onCompleted()
        assertThat(source1.hasCompleted()).isTrue()
        assertThat(source2.hasCompleted()).isTrue()
        assertThat(source3.hasCompleted()).isTrue()
    }

    /**
     * tee combines [PublishSubject]s under one PublishSubject. We need to make sure that they are not wrapped with a [SafeSubscriber].
     * Otherwise, if a non Rx exception gets thrown from a subscriber under one of the PublishSubject it will get caught by the
     * SafeSubscriber wrapping that PublishSubject and will call [PublishSubject.PublishSubjectState.onError], which will
     * eventually shut down all of the subscribers under that PublishSubject.
     */
    @Test(timeout=300_000)
    fun `error in unsafe subscriber won't shutdown subscribers under same publish subject, after tee`() {
        val source1 = PublishSubject.create<Int>()
        val source2 = PublishSubject.create<Int>()
        var count = 0

        source1.subscribe { count += it } // safe subscriber
        source1.unsafeSubscribe(Subscribers.create { throw RuntimeException() }) // this subscriber should not shut down the above subscriber

        assertFailsWith<RuntimeException> {
            source1.tee(source2).onNext(1)
        }
        assertFailsWith<RuntimeException> {
            source1.tee(source2).onNext(1)
        }
        assertEquals(2, count)
    }

    @Test(timeout=300_000)
    fun `continueOnError subscribes ResilientSubscribers, wrapped Observers will survive errors from onNext`() {
        var heartBeat1 = 0
        var heartBeat2 = 0
        val source = PublishSubject.create<Int>()
        val continueOnError = source.continueOnError()
        continueOnError.subscribe { runNo ->
            // subscribes with a ResilientSubscriber
            heartBeat1++
            if (runNo == 1) {
                throw IllegalStateException()
            }
        }
        continueOnError.subscribe { runNo ->
            // subscribes with a ResilientSubscriber
            heartBeat2++
            if (runNo == 2) {
                throw IllegalStateException()
            }
        }

        assertFailsWith<OnErrorNotImplementedException> {
            source.onNext(1) // first observer only will run and throw
        }
        assertFailsWith<OnErrorNotImplementedException> {
            source.onNext(2) // first observer will run, second observer will run and throw
        }
        source.onNext(3) // both observers will run
        assertEquals(3, heartBeat1)
        assertEquals(2, heartBeat2)
    }

    @Test(timeout=300_000)
    fun `PublishSubject unsubscribes ResilientSubscribers only upon explicitly calling onError`() {
        var heartBeat = 0
        val source = PublishSubject.create<Int>()
        source.continueOnError().subscribe { heartBeat += it }
        source.continueOnError().subscribe { heartBeat += it }
        source.onNext(1)
        // send an onError event
        assertFailsWith<CompositeException> {
            source.onError(IllegalStateException()) // all ResilientSubscribers under PublishSubject get unsubscribed here
        }
        source.onNext(1)
        assertEquals(2, heartBeat)
    }

    @Test(timeout=300_000)
    fun `PublishSubject wrapped with a SafeSubscriber shuts down the whole structure, if one of them is unsafe and it throws`() {
        var heartBeat = 0
        val source = PublishSubject.create<Int>()
        source.unsafeSubscribe(Subscribers.create { runNo -> // subscribes unsafe; It does not wrap with ResilientSubscriber
            heartBeat++
            if (runNo == 1) {
                throw IllegalStateException()
            }
        })
        source.continueOnError().subscribe { heartBeat += it }
        // wrapping PublishSubject with a SafeSubscriber
        val sourceWrapper = SafeSubscriber(Subscribers.from(source))
        assertFailsWith<OnErrorFailedException> {
            sourceWrapper.onNext(1)
        }
        sourceWrapper.onNext(2)
        assertEquals(1, heartBeat)
    }

    /**
     * A [ResilientSubscriber] that is NOT a leaf in a subscribers structure will not call [onError]
     * if an error occurs during its [onNext] event processing.
     *
     * The reason why it should not call its onError is: if it wraps a [PublishSubject], calling [ResilientSubscriber.onError]
     * will then call [PublishSubject.onError] which will shut down all the subscribers under the [PublishSubject].
     */
    @Test(timeout=300_000)
    fun `PublishSubject wrapped with a ResilientSubscriber will preserve the structure, if one of its children subscribers is unsafe and it throws`() {
        var heartBeat = 0
        val source = PublishSubject.create<Int>()
        source.unsafeSubscribe(Subscribers.create { runNo ->
            heartBeat++
            if (runNo == 1) {
                throw IllegalStateException()
            }
        })
        source.continueOnError().subscribe { heartBeat++ }
        // wrap PublishSubject with a ResilientSubscriber
        val sourceWrapper = ResilientSubscriber(Subscribers.from(source))
        assertFailsWith<OnNextFailedException>("Observer.onNext failed, this is a non leaf ResilientSubscriber, therefore onError will be skipped") {
            sourceWrapper.onNext(1)
        }
        sourceWrapper.onNext(2)
        assertEquals(3, heartBeat)
    }

    @Test(timeout=300_000)
    fun `throwing inside onNext of a ResilientSubscriber leaf subscriber will call onError`() {
        var heartBeatOnNext = 0
        var heartBeatOnError = 0
        val source = PublishSubject.create<Int>()
        // add a leaf ResilientSubscriber
        source.continueOnError().subscribe({
            heartBeatOnNext++
            throw IllegalStateException()
        }, {
            heartBeatOnError++
        })

        source.onNext(1)
        source.onNext(1)
        assertEquals(2, heartBeatOnNext)
        assertEquals(2, heartBeatOnError)
    }

    /**
     * In this test ResilientSubscriber throws an OnNextFailedException which is a OnErrorNotImplementedException.
     * Because its underlying subscriber is not an ActionSubscriber, it will not be considered as a leaf ResilientSubscriber.
     */
    @Test(timeout=300_000)
    fun `throwing ResilientSubscriber at onNext will wrap with a Rx OnErrorNotImplementedException`() {
        val resilientSubscriber = ResilientSubscriber<Int>(Subscribers.create { throw IllegalStateException() })
        assertFailsWith<OnErrorNotImplementedException> { // actually fails with an OnNextFailedException
            resilientSubscriber.onNext(1)
        }
    }

    @Test(timeout=300_000)
    fun `throwing inside ResilientSubscriber onError will wrap with a Rx OnErrorFailedException`() {
        val resilientSubscriber = ResilientSubscriber<Int>(
            ActionSubscriber(
                { throw IllegalStateException() },
                { throw IllegalStateException() },
                null
            )
        )
        assertFailsWith<OnErrorFailedException> {
            resilientSubscriber.onNext(1)
        }
    }

    /**
     * In this test we create a chain of Subscribers with this the following order:
     * ResilientSubscriber_X -> PublishSubject -> ResilientSubscriber_Y
     *
     * ResilientSubscriber_Y.onNext throws an error, since ResilientSubscriber_Y.onError is not defined,
     * it will throw a OnErrorNotImplementedException. Then it will be propagated back until ResilientSubscriber_X.
     * ResilientSubscriber_X will identify it is a not leaf subscriber and therefore will rethrow it as OnNextFailedException.
     */
    @Test(timeout=300_000)
    fun `propagated Rx exception will be rethrown at ResilientSubscriber onError`() {
        val source = PublishSubject.create<Int>()
        source.continueOnError().subscribe { throw IllegalStateException("123") } // will give a leaf ResilientSubscriber
        val sourceWrapper = ResilientSubscriber(Subscribers.from(source)) // will give an inner ResilientSubscriber

        assertFailsWith<OnNextFailedException>("Observer.onNext failed, this is a non leaf ResilientSubscriber, therefore onError will be skipped") {
            // IllegalStateException will be wrapped and rethrown as a OnErrorNotImplementedException in leaf ResilientSubscriber,
            // will be caught by inner ResilientSubscriber and just be rethrown
            sourceWrapper.onNext(1)
        }
    }

    @Test(timeout=300_000)
    fun `test OnResilientSubscribe strictMode = true replaces SafeSubscriber subclass`() {
        var heartBeat = 0
        val customSafeSubscriber = CustomSafeSubscriber(
            Subscribers.create<Int> {
                heartBeat++
                throw IllegalArgumentException()
            })

        val source = PublishSubject.create<Int>()
        source.continueOnError().subscribe(customSafeSubscriber) // it should replace CustomSafeSubscriber with ResilientSubscriber

        assertFailsWith<OnErrorNotImplementedException> { source.onNext(1) }
        assertFailsWith<OnErrorNotImplementedException> { source.onNext(1) }
        assertEquals(2, heartBeat)
    }

    @Test(timeout=300_000)
    fun `test OnResilientSubscribe strictMode = false will not replace SafeSubscriber subclass`() {
        var heartBeat = 0
        val customSafeSubscriber = CustomSafeSubscriber(
            Subscribers.create<Int> {
                heartBeat++
                throw IllegalArgumentException()
            })

        val source = PublishSubject.create<Int>()
        source.resilientOnError().subscribe(customSafeSubscriber) // it should not replace CustomSafeSubscriber with ResilientSubscriber

        assertFailsWith<OnErrorNotImplementedException> { source.onNext(1) }
        source.onNext(1)
        assertEquals(1, heartBeat)
    }

    @Test(timeout=300_000)
    fun `combine tee and bufferUntilDatabaseCommit`() {
        val database = createDatabase()

        val source = PublishSubject.create<Int>()
        val teed = PublishSubject.create<Int>()

        val observable: Observable<Int> = source

        val firstEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val teedEvent = SettableFuture.create<Pair<Int, Boolean>>()

        observable.first().subscribe { firstEvent.set(it to isInDatabaseTransaction()) }

        teed.first().subscribe { teedEvent.set(it to isInDatabaseTransaction()) }

        database.transaction {
            val delayedSubject = source.bufferUntilDatabaseCommit().tee(teed)
            assertThat(source).isNotEqualTo(delayedSubject)
            delayedSubject.onNext(0)
            assertThat(firstEvent.isDone).isFalse()
            assertThat(teedEvent.isDone).isTrue()
        }
        assertThat(firstEvent.isDone).isTrue()

        assertThat(firstEvent.get()).isEqualTo(0 to false)
        assertThat(teedEvent.get()).isEqualTo(0 to true)
    }

    @Test(timeout=300_000)
    fun `new transaction open in observer when wrapped`() {
        val database = createDatabase()

        val source = PublishSubject.create<Int>()
        val observableWithDbTx: Observable<Int> = source.wrapWithDatabaseTransaction()

        val undelayedEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val delayedEventFromSecondObserver = SettableFuture.create<Pair<Int, UUID?>>()
        val delayedEventFromThirdObserver = SettableFuture.create<Pair<Int, UUID?>>()

        observableWithDbTx.first().subscribe { undelayedEvent.set(it to isInDatabaseTransaction()) }

        fun observeSecondEvent(event: Int, future: SettableFuture<Pair<Int, UUID?>>) {
            future.set(event to if (isInDatabaseTransaction()) contextTransaction.id else null)
        }

        observableWithDbTx.skip(1).first().subscribe { observeSecondEvent(it, delayedEventFromSecondObserver) }
        observableWithDbTx.skip(1).first().subscribe { observeSecondEvent(it, delayedEventFromThirdObserver) }

        database.transaction {
            val commitDelayedSource = source.bufferUntilDatabaseCommit()
            assertThat(source).isNotEqualTo(commitDelayedSource)
            commitDelayedSource.onNext(0)
            source.onNext(1)
            assertThat(undelayedEvent.isDone).isTrue()
            assertThat(undelayedEvent.get()).isEqualTo(1 to true)
            assertThat(delayedEventFromSecondObserver.isDone).isFalse()
        }
        assertThat(delayedEventFromSecondObserver.isDone).isTrue()

        assertThat(delayedEventFromSecondObserver.get().first).isEqualTo(0)
        assertThat(delayedEventFromSecondObserver.get().second).isNotNull()
        assertThat(delayedEventFromThirdObserver.get().first).isEqualTo(0)
        assertThat(delayedEventFromThirdObserver.get().second).isNotNull()

        // Test that the two observers of the second event were notified inside the same database transaction.
        assertThat(delayedEventFromSecondObserver.get().second).isEqualTo(delayedEventFromThirdObserver.get().second)
    }

    @Test(timeout=300_000)
    fun `check wrapping in db tx doesn't eagerly subscribe`() {
        val database = createDatabase()

        val source = PublishSubject.create<Int>()
        var subscribed = false
        val event = SettableFuture.create<Int>()

        val bufferedObservable: Observable<Int> = source.bufferUntilSubscribed().doOnSubscribe { subscribed = true }
        val databaseWrappedObservable: Observable<Int> = bufferedObservable.wrapWithDatabaseTransaction(database)

        source.onNext(0)

        assertThat(subscribed).isFalse()
        assertThat(event.isDone).isFalse()

        databaseWrappedObservable.first().subscribe { event.set(it) }
        source.onNext(1)

        assertThat(event.isDone).isTrue()
        assertThat(event.get()).isEqualTo(0)
    }

    @Test(timeout=300_000)
    fun `check wrapping in db tx unsubscribes`() {
        val database = createDatabase()

        val source = PublishSubject.create<Int>()
        var unsubscribed = false

        val bufferedObservable: Observable<Int> = source.bufferUntilSubscribed().doOnUnsubscribe { unsubscribed = true }
        val databaseWrappedObservable: Observable<Int> = bufferedObservable.wrapWithDatabaseTransaction(database)

        assertThat(unsubscribed).isFalse()

        val subscription1 = databaseWrappedObservable.subscribe { }
        val subscription2 = databaseWrappedObservable.subscribe { }

        subscription1.unsubscribe()
        assertThat(unsubscribed).isFalse()

        subscription2.unsubscribe()
        assertThat(unsubscribed).isTrue()
    }

    @Test(timeout=300_000)
    fun `check wrapping in db tx restarts if we pass through zero subscribers`() {
        val database = createDatabase()

        val source = PublishSubject.create<Int>()
        var unsubscribed = false

        val bufferedObservable: Observable<Int> = source.doOnUnsubscribe { unsubscribed = true }
        val databaseWrappedObservable: Observable<Int> = bufferedObservable.wrapWithDatabaseTransaction(database)

        assertThat(unsubscribed).isFalse()

        val subscription1 = databaseWrappedObservable.subscribe { }
        val subscription2 = databaseWrappedObservable.subscribe { }

        subscription1.unsubscribe()
        assertThat(unsubscribed).isFalse()

        subscription2.unsubscribe()
        assertThat(unsubscribed).isTrue()

        val event = SettableFuture.create<Int>()
        val subscription3 = databaseWrappedObservable.subscribe { event.set(it) }

        source.onNext(1)

        assertThat(event.isDone).isTrue()
        assertThat(event.get()).isEqualTo(1)

        subscription3.unsubscribe()
    }

    class CustomSafeSubscriber<T>(actual: Subscriber<in T>): SafeSubscriber<T>(actual)
}