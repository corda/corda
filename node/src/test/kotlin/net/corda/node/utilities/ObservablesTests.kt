package net.corda.node.utilities

import com.google.common.util.concurrent.SettableFuture
import net.corda.core.bufferUntilSubscribed
import net.corda.core.tee
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class ObservablesTests {

    private fun isInDatabaseTransaction(): Boolean = (TransactionManager.currentOrNull() != null)

    @Test
    fun `bufferUntilDatabaseCommit delays until transaction closed`() {
        val (toBeClosed, database) = configureDatabase(makeTestDataSourceProperties())

        val subject = PublishSubject.create<Int>()
        val observable: Observable<Int> = subject

        val firstEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val secondEvent = SettableFuture.create<Pair<Int, Boolean>>()

        observable.first().subscribe { firstEvent.set(it to isInDatabaseTransaction()) }
        observable.skip(1).first().subscribe { secondEvent.set(it to isInDatabaseTransaction()) }

        databaseTransaction(database) {
            val delayedSubject = subject.bufferUntilDatabaseCommit()
            assertThat(subject).isNotEqualTo(delayedSubject)
            delayedSubject.onNext(0)
            subject.onNext(1)
            assertThat(firstEvent.isDone).isTrue()
            assertThat(secondEvent.isDone).isFalse()
        }
        assertThat(secondEvent.isDone).isTrue()

        assertThat(firstEvent.get()).isEqualTo(1 to true)
        assertThat(secondEvent.get()).isEqualTo(0 to false)

        toBeClosed.close()
    }

    @Test
    fun `bufferUntilDatabaseCommit delays until transaction closed repeatable`() {
        val (toBeClosed, database) = configureDatabase(makeTestDataSourceProperties())

        val subject = PublishSubject.create<Int>()
        val observable: Observable<Int> = subject

        val firstEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val secondEvent = SettableFuture.create<Pair<Int, Boolean>>()

        observable.first().subscribe { firstEvent.set(it to isInDatabaseTransaction()) }
        observable.skip(1).first().subscribe { secondEvent.set(it to isInDatabaseTransaction()) }

        databaseTransaction(database) {
            val delayedSubject = subject.bufferUntilDatabaseCommit()
            assertThat(subject).isNotEqualTo(delayedSubject)
            delayedSubject.onNext(0)
            assertThat(firstEvent.isDone).isFalse()
            assertThat(secondEvent.isDone).isFalse()
        }
        assertThat(firstEvent.isDone).isTrue()
        assertThat(firstEvent.get()).isEqualTo(0 to false)
        assertThat(secondEvent.isDone).isFalse()

        databaseTransaction(database) {
            val delayedSubject = subject.bufferUntilDatabaseCommit()
            assertThat(subject).isNotEqualTo(delayedSubject)
            delayedSubject.onNext(1)
            assertThat(secondEvent.isDone).isFalse()
        }
        assertThat(secondEvent.isDone).isTrue()
        assertThat(secondEvent.get()).isEqualTo(1 to false)

        toBeClosed.close()
    }

    @Test
    fun `tee correctly copies observations to multiple observers`() {

        val subject1 = PublishSubject.create<Int>()
        val subject2 = PublishSubject.create<Int>()
        val subject3 = PublishSubject.create<Int>()

        val event1 = SettableFuture.create<Int>()
        val event2 = SettableFuture.create<Int>()
        val event3 = SettableFuture.create<Int>()

        subject1.subscribe { event1.set(it) }
        subject2.subscribe { event2.set(it) }
        subject3.subscribe { event3.set(it) }

        val tee = subject1.tee(subject2, subject3)
        tee.onNext(0)

        assertThat(event1.isDone).isTrue()
        assertThat(event2.isDone).isTrue()
        assertThat(event3.isDone).isTrue()
        assertThat(event1.get()).isEqualTo(0)
        assertThat(event2.get()).isEqualTo(0)
        assertThat(event3.get()).isEqualTo(0)

        tee.onCompleted()
        assertThat(subject1.hasCompleted()).isTrue()
        assertThat(subject2.hasCompleted()).isTrue()
        assertThat(subject3.hasCompleted()).isTrue()
    }

    @Test
    fun `combine tee and bufferUntilDatabaseCommit`() {
        val (toBeClosed, database) = configureDatabase(makeTestDataSourceProperties())

        val subject = PublishSubject.create<Int>()
        val teed = PublishSubject.create<Int>()

        val observable: Observable<Int> = subject

        val firstEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val teedEvent = SettableFuture.create<Pair<Int, Boolean>>()

        observable.first().subscribe { firstEvent.set(it to isInDatabaseTransaction()) }

        teed.first().subscribe { teedEvent.set(it to isInDatabaseTransaction()) }

        databaseTransaction(database) {
            val delayedSubject = subject.bufferUntilDatabaseCommit().tee(teed)
            assertThat(subject).isNotEqualTo(delayedSubject)
            delayedSubject.onNext(0)
            assertThat(firstEvent.isDone).isFalse()
            assertThat(teedEvent.isDone).isTrue()
        }
        assertThat(firstEvent.isDone).isTrue()

        assertThat(firstEvent.get()).isEqualTo(0 to false)
        assertThat(teedEvent.get()).isEqualTo(0 to true)

        toBeClosed.close()
    }

    @Test
    fun `new transaction open in observer when wrapped`() {
        val (toBeClosed, database) = configureDatabase(makeTestDataSourceProperties())

        val subject = PublishSubject.create<Int>()
        val observable: Observable<Int> = subject.wrapWithDatabaseTransaction()

        val firstEvent = SettableFuture.create<Pair<Int, Boolean>>()
        val secondEvent1 = SettableFuture.create<Pair<Int, UUID?>>()
        val secondEvent2 = SettableFuture.create<Pair<Int, UUID?>>()

        observable.first().subscribe { firstEvent.set(it to isInDatabaseTransaction()) }
        observable.skip(1).first().subscribe { secondEvent1.set(it to if (isInDatabaseTransaction()) StrandLocalTransactionManager.transactionId else null) }
        observable.skip(1).first().subscribe { secondEvent2.set(it to if (isInDatabaseTransaction()) StrandLocalTransactionManager.transactionId else null) }

        databaseTransaction(database) {
            val delayedSubject = subject.bufferUntilDatabaseCommit()
            assertThat(subject).isNotEqualTo(delayedSubject)
            delayedSubject.onNext(0)
            subject.onNext(1)
            assertThat(firstEvent.isDone).isTrue()
            assertThat(secondEvent1.isDone).isFalse()
        }
        assertThat(secondEvent1.isDone).isTrue()

        assertThat(firstEvent.get()).isEqualTo(1 to true)
        assertThat(secondEvent1.get().first).isEqualTo(0)
        assertThat(secondEvent1.get().second).isNotNull()
        assertThat(secondEvent2.get().first).isEqualTo(0)
        assertThat(secondEvent2.get().second).isNotNull()

        // Test that the two observers of the second event were notified inside the same database transaction.
        assertThat(secondEvent1.get().second).isEqualTo(secondEvent2.get().second)

        toBeClosed.close()
    }

    @Test
    fun `check wrapping doesn't eagerly subscribe`() {
        val (toBeClosed, database) = configureDatabase(makeTestDataSourceProperties())

        val subject = PublishSubject.create<Int>()
        var subscribed = false
        val event = SettableFuture.create<Int>()

        val observable1: Observable<Int> = subject.bufferUntilSubscribed().doOnSubscribe { subscribed = true }
        val observable2: Observable<Int> = observable1.wrapWithDatabaseTransaction(database)

        subject.onNext(0)

        assertThat(subscribed).isFalse()
        assertThat(event.isDone).isFalse()

        observable2.first().subscribe { event.set(it) }
        subject.onNext(1)

        assertThat(event.isDone).isTrue()
        assertThat(event.get()).isEqualTo(0)

        toBeClosed.close()
    }


    @Test
    fun `check wrapping unsubscribes`() {
        val (toBeClosed, database) = configureDatabase(makeTestDataSourceProperties())

        val subject = PublishSubject.create<Int>()
        var unsubscribed = false

        val observable1: Observable<Int> = subject.bufferUntilSubscribed().doOnUnsubscribe { unsubscribed = true }
        val observable2: Observable<Int> = observable1.wrapWithDatabaseTransaction(database)

        assertThat(unsubscribed).isFalse()

        val subscription1 = observable2.subscribe { }
        val subscription2 = observable2.subscribe { }

        subscription1.unsubscribe()
        assertThat(unsubscribed).isFalse()

        subscription2.unsubscribe()
        assertThat(unsubscribed).isTrue()

        toBeClosed.close()
    }
}