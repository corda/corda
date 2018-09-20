package net.corda.tools.error.codes.server

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import javax.annotation.PreDestroy
import javax.inject.Named

@Named
internal class VertxSupplier : () -> Vertx, AutoCloseable {

    private val instance = Vertx.vertx(options())

    override fun invoke(): Vertx = instance

    @PreDestroy
    override fun close() {

        instance.apply {
            deploymentIDs().forEach(::undeploy)
        }
    }

    private fun options(): VertxOptions {

        return VertxOptions()
    }
}