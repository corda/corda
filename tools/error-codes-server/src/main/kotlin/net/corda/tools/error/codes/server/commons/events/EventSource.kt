package net.corda.tools.error.codes.server.commons.events

import reactor.core.publisher.Flux

interface EventSource<out EVENT : AbstractEvent> {

    val events: Flux<out EVENT>
}