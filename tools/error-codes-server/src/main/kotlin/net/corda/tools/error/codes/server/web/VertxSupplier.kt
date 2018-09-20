package net.corda.tools.error.codes.server.web

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import net.corda.tools.error.codes.server.context.loggerFor
import javax.annotation.PreDestroy
import javax.inject.Named

@Named
internal class VertxSupplier : () -> Vertx, AutoCloseable {

    private companion object {

        private val logger = loggerFor<VertxSupplier>()
    }

    private val instance = Vertx.vertx(options())

    override fun invoke(): Vertx = instance

    @PreDestroy
    override fun close() {

        instance.apply {
            deploymentIDs().forEach(::undeploy)
        }
        logger.info("Closed.")
    }

    private fun options(): VertxOptions {

        return VertxOptions()
    }
}