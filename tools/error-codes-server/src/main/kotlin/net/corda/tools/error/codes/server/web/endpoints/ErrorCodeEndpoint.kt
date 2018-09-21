package net.corda.tools.error.codes.server.web.endpoints

import com.uchuhimo.konf.Config
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.Router
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.web.endpoints.template.ConfigurableEndpoint
import net.corda.tools.error.codes.server.web.endpoints.template.EndpointConfigProvider
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@Named
internal class ErrorCodeEndpoint @Inject constructor(configuration: ErrorCodeEndpoint.Configuration, private val locateDescription: (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>>) : ConfigurableEndpoint(configuration, setOf(HttpMethod.GET)) {

    private companion object {

        private const val ERROR_CODE = "error_code"
    }

    override fun install(router: Router) {

        serve(router.get(path)) { context ->

            withPathParam(ERROR_CODE, ErrorCode.Valid::create, context) { errorCode ->

                locateDescription(errorCode, context).thenIfPresent(response()) { location -> response().end(location) }
            }
        }
    }

    private fun HttpServerResponse.end(location: ErrorDescriptionLocation) {

        when (location) {
            is ErrorDescriptionLocation.External -> location.writeTo(this).end()
        }
    }

    private fun ErrorDescriptionLocation.External.writeTo(response: HttpServerResponse): HttpServerResponse {

        return response.putHeader(HttpHeaderNames.LOCATION, uri.toASCIIString()).setStatusCode(HttpResponseStatus.TEMPORARY_REDIRECT.code())
    }

    interface Configuration : ConfigurableEndpoint.Configuration

    @Named
    internal class ErrorCodeConfigProvider @Inject constructor(applyConfigStandards: (Config) -> Config) : ErrorCodeEndpoint.Configuration, EndpointConfigProvider(CONFIGURATION_SECTION_PATH, applyConfigStandards) {

        private companion object {

            private const val CONFIGURATION_SECTION_PATH = "configuration.web.server.endpoints.error_code"
        }
    }
}