package net.corda.tools.error.codes.server.web.endpoints.template

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import net.corda.tools.error.codes.server.commons.domain.validation.ValidationResult
import net.corda.tools.error.codes.server.commons.web.vertx.Endpoint
import net.corda.tools.error.codes.server.commons.web.vertx.VertxEndpoint
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.loggerFor
import reactor.core.Disposable
import reactor.core.publisher.Mono

internal abstract class ConfigurableEndpoint(configuration: Configuration, override val methods: Set<HttpMethod>) : VertxEndpoint(), Endpoint {

    private companion object {

        private val logger = loggerFor<ConfigurableEndpoint>()
    }

    final override val path = configuration.path
    final override val name = configuration.name
    final override val enabled = configuration.enabled

    protected open fun Route.withDefaults(): Route {

        return failureHandler(::handleFailure)
    }

    protected open fun reportError(error: Throwable, response: HttpServerResponse, invocationContext: InvocationContext?) {

        if (error is RequestValidationException) {
            response.endBecauseUnprocessable(error.errors)
        } else {
            logger.error(invocationContext, error.message, error)
            response.endWithInternalError()
        }
    }

    protected open fun handleFailure(ctx: RoutingContext) = reportError(ctx.failure(), ctx.response(), ctx[InvocationContext::class.java.name])

    protected fun <ELEMENT : Any> Mono<ELEMENT>.thenIfPresent(ctx: RoutingContext, action: (ELEMENT) -> Unit): Disposable {

        return doOnSuccess { result: ELEMENT? ->

            if (result == null) {
                ctx.response().endWithNotFound()
            } else {
                action.invoke(result)
            }
        }.subscribe({}, { error: Throwable -> reportError(error, ctx.response(), ctx[InvocationContext::class.java.name]) })
    }

    protected fun HttpServerResponse.endWithNotFound() {

        val json = JsonObject()
        json["error"] = "The requested resource was not found."
        setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end(json)
    }

    protected fun HttpServerResponse.endWithErrorMessage(message: String, responseStatus: HttpResponseStatus) {

        val json = JsonObject()
        json["error"] = message
        setStatusCode(responseStatus.code()).end(json)
    }

    protected fun HttpServerResponse.endBecauseUnprocessable(errors: Set<String>) {

        val json = JsonObject()
        json["errors"] = errors.fold(JsonArray(), JsonArray::add)
        setStatusCode(HttpResponseStatus.UNPROCESSABLE_ENTITY.code()).end(json)
    }

    protected fun HttpServerResponse.endWithInternalError() {

        endWithErrorMessage("An unexpected error occurred.", HttpResponseStatus.INTERNAL_SERVER_ERROR)
    }

    protected fun serve(route: Route, action: RoutingContext.(InvocationContext) -> Unit) {

        route.withDefaults().handler { ctx ->
            val invocationContext = ctx.deriveInvocationContext()
            ctx.put(InvocationContext::class.java.name, invocationContext)
            action.invoke(ctx, invocationContext)
        }
    }

    protected fun <PARAM : Any> RoutingContext.withPathParam(paramName: String, convert: (String) -> ValidationResult<PARAM>, action: (PARAM) -> Unit) {

        val specifiedErrorCode = pathParam(paramName)?.let(convert) ?: throw RequestValidationException.withError("Unspecified path param \"$paramName\".")
        val value = specifiedErrorCode.validValue { errors -> RequestValidationException.withErrors("Invalid path param \"$paramName\".", errors) }
        action.invoke(value)
    }

    private fun RoutingContext.deriveInvocationContext(): InvocationContext = InvocationContext.newInstance()

    internal interface Configuration {

        val name: String
        val path: String
        val enabled: Boolean
    }
}

internal abstract class EndpointConfigProvider(sectionPath: String, applyConfigStandards: (Config) -> Config) : ConfigurableEndpoint.Configuration {

    private val spec = EndpointConfSpec(sectionPath)

    private val config = applyConfigStandards.invoke(Config { addSpec(spec) })

    override val name: String = config[spec.name]
    override val path: String = config[spec.path]
    override val enabled: Boolean = config[spec.enabled]

    private class EndpointConfSpec(sectionPath: String) : ConfigSpec(sectionPath) {

        val name by required<String>()
        val path by required<String>()
        val enabled by optional(true)
    }
}