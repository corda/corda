package net.corda.tools.error.codes.server.web.endpoints

import com.uchuhimo.konf.Config
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import net.corda.tools.error.codes.server.commons.web.vertx.Endpoint
import javax.inject.Inject
import javax.inject.Named

@Named
internal class HealthCheckEndpoint @Inject constructor(configuration: HealthCheckEndpoint.Configuration) : Endpoint {

    // TODO sollecitom introduce a map-based functional Endpoint template
    override fun install(router: Router) {

        router.get(path).failureHandler(::handleFailure).handler { ctx -> ctx.response().setStatusCode(HttpResponseStatus.OK.code()).end() }
    }

    // TODO move to supertype
    override val path = configuration.path
    override val name = configuration.name
    override val enabled = configuration.enabled

    override val methods: Set<HttpMethod> = setOf(HttpMethod.GET)

    interface Configuration : EndpointConfiguration

    // TODO sollecitom move to supertype
    private fun handleFailure(ctx: RoutingContext) {


        // TODO sollecitom publish event and log
        val json = JsonObject()
        json["error"] = "An unexpected error occurred."
        ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(json)
    }

    // TODO sollecitom move to supertype
    private operator fun JsonObject.set(key: String, value: Any?) {

        put(key, value)
    }

    // TODO sollecitom move to supertype
    private fun HttpServerResponse.end(json: JsonObject) = putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON).end(json.toBuffer())

    // TODO sollecitom move to supertype
    private fun HttpServerResponse.end(json: JsonArray) = putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON).end(json.toBuffer())

    @Named
    internal class HealthCheckConfigProvider @Inject constructor(applyConfigStandards: (Config) -> Config) : HealthCheckEndpoint.Configuration, EndpointConfigProvider(CONFIGURATION_SECTION_PATH, applyConfigStandards) {

        private companion object {

            private const val CONFIGURATION_SECTION_PATH = "configuration.web.server.endpoints.healthcheck"
        }
    }
}

