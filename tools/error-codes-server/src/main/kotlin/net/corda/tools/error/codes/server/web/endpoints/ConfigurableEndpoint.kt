package net.corda.tools.error.codes.server.web.endpoints

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import net.corda.tools.error.codes.server.commons.web.vertx.Endpoint
import net.corda.tools.error.codes.server.commons.web.vertx.VertxEndpoint
import net.corda.tools.error.codes.server.context.InvocationContext
import net.corda.tools.error.codes.server.context.loggerFor

internal abstract class ConfigurableEndpoint(configuration: ConfigurableEndpoint.Configuration, override val methods: Set<HttpMethod>) : VertxEndpoint(), Endpoint {

    private companion object {

        private val logger = loggerFor<ConfigurableEndpoint>()
    }

    final override val path = configuration.path
    final override val name = configuration.name
    final override val enabled = configuration.enabled

    protected open fun Route.withDefaults(): Route {

        return failureHandler(::handleFailure)
    }

    protected open fun handleFailure(ctx: RoutingContext) {

        val json = JsonObject()
        json["error"] = "An unexpected error occurred."
        ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(json)
        logger.error(ctx.failure().message, ctx.failure())
    }

    protected fun RoutingContext.invocation(): InvocationContext = InvocationContext.newInstance()

    internal interface Configuration {

        val name: String
        val path: String
        val enabled: Boolean
    }
}

internal abstract class EndpointConfigProvider(sectionPath: String, applyConfigStandards: (Config) -> Config) : ConfigurableEndpoint.Configuration {

    private val spec = EndpointConfSpec(sectionPath)

    private val config = applyConfigStandards.invoke(Config { addSpec(spec) })

    // TODO sollecitom add validation
    override val name: String = config[spec.name]
    override val path: String = config[spec.path]
    override val enabled: Boolean = config[spec.enabled]

    private class EndpointConfSpec(sectionPath: String) : ConfigSpec(sectionPath) {

        val name by required<String>()
        val path by required<String>()
        val enabled by optional(true)
    }
}