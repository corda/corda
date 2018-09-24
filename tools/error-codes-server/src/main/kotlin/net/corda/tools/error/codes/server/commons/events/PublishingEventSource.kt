package net.corda.tools.error.codes.server.commons.events

import reactor.core.publisher.EmitterProcessor
import reactor.core.publisher.Flux
import java.time.Duration
import javax.annotation.PreDestroy

abstract class PublishingEventSource<EVENT : AbstractEvent> : EventSource<EVENT>, EventSink<EVENT>, AutoCloseable {

    private val processor = EmitterProcessor.create<EVENT>()

    override val events: Flux<EVENT> = processor

    override fun publish(event: EVENT) = processor.onNext(event)

    @PreDestroy
    override fun close() {

        processor.onComplete()
    }
}