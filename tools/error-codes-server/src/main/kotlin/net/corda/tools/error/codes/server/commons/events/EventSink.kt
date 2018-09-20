package net.corda.tools.error.codes.server.commons.events

interface EventSink<in EVENT : Event> {

    fun publish(event: EVENT)
}