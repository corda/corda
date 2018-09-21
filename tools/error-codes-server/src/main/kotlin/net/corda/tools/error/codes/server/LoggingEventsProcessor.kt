package net.corda.tools.error.codes.server

import net.corda.tools.error.codes.server.commons.events.EventStream
import net.corda.tools.error.codes.server.commons.reactive.only
import net.corda.tools.error.codes.server.domain.loggerFor
import net.corda.tools.error.codes.server.web.WebServerEvent
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Named

@Named
internal class LoggingEventsProcessors @Inject internal constructor(stream: EventStream) {

    private companion object {

        private val logger = loggerFor<LoggingEventsProcessors>()
    }

    init {
        with(stream) {
            events.only<WebServerEvent.Initialisation.Completed>().doOnNext(::logWebServerInitialisation).subscribe()
        }
    }

    private fun logWebServerInitialisation(event: WebServerEvent.Initialisation.Completed) {

        logger.info("Web server started listening on port '${event.port.value}' at ${LocalDateTime.ofInstant(event.createdAt, ZoneId.systemDefault())}. Event ID is '${event.id.value}'.")
    }
}