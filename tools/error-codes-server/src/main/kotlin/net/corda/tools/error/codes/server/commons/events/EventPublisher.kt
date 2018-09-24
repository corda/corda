package net.corda.tools.error.codes.server.commons.events

interface EventPublisher<EVENT : AbstractEvent> {

    val source: EventSource<EVENT>

    val events get() = source.events
}