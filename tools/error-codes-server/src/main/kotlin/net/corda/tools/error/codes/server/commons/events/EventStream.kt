package net.corda.tools.error.codes.server.commons.events

import reactor.core.publisher.Flux

interface EventStream {

    val events: Flux<out Event>
}