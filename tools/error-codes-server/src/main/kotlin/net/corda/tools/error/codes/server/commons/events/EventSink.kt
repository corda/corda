package net.corda.tools.error.codes.server.commons.events

interface EventSink<in EVENT : AbstractEvent> {

    fun publish(event: EVENT)
}