package net.corda.tools.error.codes.server.commons.events

import net.corda.tools.error.codes.server.domain.loggerFor
import reactor.core.publisher.EmitterProcessor
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.io.Closeable
import java.time.Duration
import javax.annotation.PreDestroy

abstract class MultiplexingEventStream constructor(sources: List<EventSource<AbstractEvent>>) : EventStream, Closeable {

    private companion object {

        private val EVENTS_LOG_TTL = Duration.ofSeconds(5)
        private val logger = loggerFor<MultiplexingEventStream>()
    }

    private val processor = EmitterProcessor.create<AbstractEvent>()

    // This is to ensure a subscriber receives events that were published up to 5 seconds before. It helps during initialisation.
    // The trailing `publishOn(Schedulers.parallel())` is to force subscribers to run on a thread pool, to avoid deadlocks.
    final override val events: Flux<AbstractEvent> = processor.cache(EVENTS_LOG_TTL).publishOn(Schedulers.parallel())

    init {
        val stream = sources.map(EventSource<AbstractEvent>::events).foldRight<Flux<out AbstractEvent>, Flux<AbstractEvent>>(Flux.empty()) { current, accumulator -> accumulator.mergeWith(current) }
        stream.subscribe { event -> processor.onNext(event) }
    }

    @PreDestroy
    final override fun close() {

        processor.onComplete()
        logger.info("Closed")
    }
}