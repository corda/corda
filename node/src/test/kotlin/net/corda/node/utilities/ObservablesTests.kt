/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import com.google.common.util.concurrent.SettableFuture
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.tee
import net.corda.node.internal.configureDatabase
import net.corda.nodeapi.internal.persistence.*
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import rx.Observable
import rx.subjects.PublishSubject
import java.io.Closeable
import java.util.*

class ObservablesTests {
    private fun isInDatabaseTransaction() = contextTransactionOrNull != null
    private val toBeClosed = mutableListOf<Closeable>()

    private fun createDatabase(): CordaPersistence {
        val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true), rigorousMock())
        toBeClosed += database
        return database
    }

    @After
    fun after() {
        toBeClosed.forEach { it.close() }
        toBeClosed.clear()
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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
}