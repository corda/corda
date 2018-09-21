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
import net.corda.tools.error.codes.server.web.endpoints.template.RequestValidationException
import reactor.core.publisher.Mono
import java.net.URI
import javax.inject.Inject
import javax.inject.Named

@Named
internal class ErrorCodeEndpoint @Inject constructor(configuration: ErrorCodeEndpoint.Configuration) : ConfigurableEndpoint(configuration, setOf(HttpMethod.GET)) {

    private companion object {

        private const val ERROR_CODE = "error_code"
    }

    override fun install(router: Router) {

        // TODO sollecitom add a function in the supertype to better do this.
        router.get(path).withDefaults().handler {

            val invocationContext = it.invocationContext()
            val specifiedErrorCode = it.pathParam(ERROR_CODE)?.let(ErrorCode.Valid::create) ?: throw RequestValidationException("Unspecified error code", invocationContext)
            val errorCode = specifiedErrorCode.validValue { errors -> RequestValidationException(errors, invocationContext) }

            lookupErrorDescriptionLocation(errorCode).subscribeWith(it) { location ->
                when (location) {
                    is ErrorDescriptionLocation.External -> it.response().putHeader(HttpHeaderNames.LOCATION, location.uri.toASCIIString()).setStatusCode(HttpResponseStatus.TEMPORARY_REDIRECT.code()).end()
                }
            }
        }
    }

    // TODO sollecitom use a Service instead
    private fun lookupErrorDescriptionLocation(errorCode: ErrorCode): Mono<ErrorDescriptionLocation> {

        return Mono.just(ErrorDescriptionLocation.External(URI.create("https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer"), errorCode))
    }

    interface Configuration : ConfigurableEndpoint.Configuration

    @Named
    internal class ErrorCodeConfigProvider @Inject constructor(applyConfigStandards: (Config) -> Config) : ErrorCodeEndpoint.Configuration, EndpointConfigProvider(CONFIGURATION_SECTION_PATH, applyConfigStandards) {

        private companion object {

            private const val CONFIGURATION_SECTION_PATH = "configuration.web.server.endpoints.error_code"
        }
    }
}