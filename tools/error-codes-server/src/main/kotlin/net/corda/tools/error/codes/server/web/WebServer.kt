package net.corda.tools.error.codes.server.web

import net.corda.tools.error.codes.server.context.loggerFor
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Named

@Named
internal class WebServer : AutoCloseable {

    private companion object {

        private val logger = loggerFor<WebServer>()
    }

    @PostConstruct
    fun start() {

        logger.info("Web server starting.")
    }

    @PreDestroy
    override fun close() {

        logger.info("Web server stopping.")
    }
}