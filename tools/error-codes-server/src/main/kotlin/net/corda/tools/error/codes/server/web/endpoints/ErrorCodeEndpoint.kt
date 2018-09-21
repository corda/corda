package net.corda.tools.error.codes.server.web.endpoints

import com.uchuhimo.konf.Config
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.web.endpoints.template.ConfigurableEndpoint
import net.corda.tools.error.codes.server.web.endpoints.template.EndpointConfigProvider
import java.net.URI
import javax.inject.Inject
import javax.inject.Named

@Named
internal class ErrorCodeEndpoint @Inject constructor(configuration: ErrorCodeEndpoint.Configuration) : ConfigurableEndpoint(configuration, setOf(HttpMethod.GET)) {

    private companion object {

        private const val ERROR_CODE = "error_code"
    }

    override fun install(router: Router) {

        router.get(path).withDefaults().handler { ctx ->

            // TODO sollecitom add validation e.g., Valid<ErrorCode> with a `fun <RAW, PARSED> validate(RAW, (RAW) -> Valid(PARSED))`.
            val errorCode = ctx.pathParam(ERROR_CODE)?.let(::ErrorCode)
            // TODO sollecitom, remove the second clause after making ErrorCode self-validating.
            if (errorCode == null) {
                ctx.response().writeErrorMessage("An unexpected error occurred.", HttpResponseStatus.BAD_REQUEST)
                return@handler
            }
            // TODO sollecitom use a Service instead - with reactive semantics
            val location: ErrorDescriptionLocation = ErrorDescriptionLocation.External(URI.create("https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer"), errorCode)
            when (location) {
                is ErrorDescriptionLocation.External -> ctx.response().putHeader(HttpHeaderNames.LOCATION, location.uri.toASCIIString()).setStatusCode(HttpResponseStatus.TEMPORARY_REDIRECT.code()).end()
            }
        }
    }

    interface Configuration : ConfigurableEndpoint.Configuration

    @Named
    internal class ErrorCodeConfigProvider @Inject constructor(applyConfigStandards: (Config) -> Config) : ErrorCodeEndpoint.Configuration, EndpointConfigProvider(CONFIGURATION_SECTION_PATH, applyConfigStandards) {

        private companion object {

            private const val CONFIGURATION_SECTION_PATH = "configuration.web.server.endpoints.error_code"
        }
    }
}