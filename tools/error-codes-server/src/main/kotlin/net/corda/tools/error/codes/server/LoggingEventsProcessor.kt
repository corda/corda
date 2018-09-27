package net.corda.tools.error.codes.server

import net.corda.tools.error.codes.server.application.ErrorDescriptionService
import net.corda.tools.error.codes.server.commons.events.EventStream
import net.corda.tools.error.codes.server.domain.loggerFor
import net.corda.tools.error.codes.server.web.WebServer
import reactor.core.publisher.ofType
import reactor.core.scheduler.Schedulers
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
        with(stream.events.publishOn(Schedulers.elastic())) {
            ofType<WebServer.Event.Initialisation.Completed>().doOnNext(::logWebServerInitialisation).subscribe()
            ofType<ErrorDescriptionService.Event.Invocation.Completed.DescriptionLocationFor.WithoutDescriptionLocation>().doOnNext(::warnAboutUnmappedErrorCode).subscribe()
        }
    }

    private fun logWebServerInitialisation(event: WebServer.Event.Initialisation.Completed) {

        logger.info("Web server started listening on port '${event.port.value}' at ${LocalDateTime.ofInstant(event.createdAt, ZoneId.systemDefault())}. Event ID is '${event.id.value}'.")
    }

    private fun warnAboutUnmappedErrorCode(event: ErrorDescriptionService.Event.Invocation.Completed.DescriptionLocationFor.WithoutDescriptionLocation) {

        // Here we could let ourselves know that an error code has no description, so that we can then go and add it to the mappings.
        logger.warn(event.invocationContext, "No description location known for error code \"${event.errorCode.value}\" at ${LocalDateTime.ofInstant(event.createdAt, ZoneId.systemDefault())}. Event ID is '${event.id.value}'.")
    }
}