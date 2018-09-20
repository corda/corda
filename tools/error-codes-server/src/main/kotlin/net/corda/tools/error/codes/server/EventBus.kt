package net.corda.tools.error.codes.server

import net.corda.tools.error.codes.server.commons.events.Event
import net.corda.tools.error.codes.server.commons.events.EventSource
import net.corda.tools.error.codes.server.commons.events.MultiplexingEventStream
import javax.inject.Inject
import javax.inject.Named

@Named
class EventBus @Inject constructor(sources: List<EventSource<Event>>) : MultiplexingEventStream(sources)