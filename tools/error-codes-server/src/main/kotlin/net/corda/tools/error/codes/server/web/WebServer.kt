package net.corda.tools.error.codes.server.web

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import net.corda.tools.error.codes.server.context.loggerFor
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Named

@Named
internal class WebServer @Inject constructor(private val vertxSupplier: () -> Vertx) : AutoCloseable {

    private companion object {

        private val logger = loggerFor<WebServer>()
    }

    // TODO sollecitom inject a domain one instead instead
    private val options = HttpServerOptions().setPort(8080)

    val server: HttpServer

    init {
        val vertx = vertxSupplier.invoke()
        val router = Router.router(vertx)

        server = vertx.createHttpServer(options).requestHandler(router::accept)
    }

    @PostConstruct
    fun start() {

        server.listen()
        logger.info("Started")
    }

    @PreDestroy
    override fun close() {

        server.close()
        logger.info("Closed.")
    }
}