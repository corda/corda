package net.corda.node.events.bus

import net.corda.node.events.Event
import rx.Observable
import rx.subjects.PublishSubject

interface EventBus : EventsSink, EventsSource, AutoCloseable

interface EventsSink {

    fun publish(event: Event)
}

interface EventsSource {

    // we might consider Project Reactor for business events framework
    val events: Observable<Event>
}

internal class InMemoryEventBus : EventBus {

    override fun publish(event: Event) {

        events.onNext(event)
    }

    override val events: PublishSubject<Event> = PublishSubject.create<Event>()

    override fun close() {

        events.onCompleted()
    }
}