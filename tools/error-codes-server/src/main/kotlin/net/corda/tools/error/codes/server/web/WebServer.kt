package net.corda.tools.error.codes.server.web

import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import net.corda.tools.error.codes.server.commons.domain.identity.set
import net.corda.tools.error.codes.server.commons.events.AbstractEvent
import net.corda.tools.error.codes.server.commons.events.EventId
import net.corda.tools.error.codes.server.commons.events.EventPublisher
import net.corda.tools.error.codes.server.commons.events.PublishingEventSource
import net.corda.tools.error.codes.server.commons.lifecycle.WithLifeCycle
import net.corda.tools.error.codes.server.commons.web.Port
import net.corda.tools.error.codes.server.commons.web.vertx.Endpoint
import net.corda.tools.error.codes.server.domain.loggerFor
import org.apache.commons.lang3.builder.ToStringBuilder
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Named

interface WebServer : EventPublisher<WebServer.Event>, WithLifeCycle {

    val options: WebServer.Options

    interface Options {

        val port: Port
    }

    sealed class Event(id: EventId = EventId.newInstance()) : AbstractEvent(id) {

        sealed class Initialisation(id: EventId = EventId.newInstance()) : WebServer.Event(id) {

            class Completed(val port: Port, id: EventId = EventId.newInstance()) : WebServer.Event.Initialisation(id) {

                override fun appendToStringElements(toString: ToStringBuilder) {

                    super.appendToStringElements(toString)
                    toString["port"] = port.value
                }
            }
        }
    }
}

@Named
internal class VertxWebServer @Inject constructor(override val options: WebServer.Options, val endpoints: Set<Endpoint>, @Named(eventSourceQualifier) override val source: PublishingEventSource<WebServer.Event> = EventSourceBean(), vertxSupplier: () -> Vertx) : WebServer {

    private companion object {

        private const val eventSourceQualifier = "VertxWebServer_PublishingEventSource"
        private val logger = loggerFor<VertxWebServer>()
    }

    private val servers: Collection<HttpServer>

    init {
        val vertx = vertxSupplier.invoke()
        val router = Router.router(vertx)
        endpoints.filter(Endpoint::enabled).forEach { it.install(router) }
        servers = (1..optimalNumberOfEventLoops()).map { vertx.createHttpServer(options.toVertx()).requestHandler(router::accept) }
    }

    @PostConstruct
    override fun start() {

        servers.forEach { it.listen() }
        logger.info("Endpoints are:${System.lineSeparator()}${endpoints.asSequence().sortedBy(Endpoint::path).joinToString(System.lineSeparator(), transform = { "\t- ${it.description()}" })}")
        source.publish(WebServer.Event.Initialisation.Completed(Port(servers.first().actualPort())))
    }

    @PreDestroy
    override fun close() {

        servers.forEach { it.close() }
        logger.info("Closed")
    }

    private fun WebServer.Options.toVertx(): HttpServerOptions = HttpServerOptions().setPort(port.value)

    private fun optimalNumberOfEventLoops(): Int = Runtime.getRuntime().availableProcessors() * 2

    @Named(eventSourceQualifier)
    private class EventSourceBean : PublishingEventSource<WebServer.Event>()
}

private fun Endpoint.description(): String = "\"$name\"${if (enabled) "" else " (DISABLED)"} on path \"$path\" ${methods.joinToString(", ", "[", "]", transform = HttpMethod::name)}"