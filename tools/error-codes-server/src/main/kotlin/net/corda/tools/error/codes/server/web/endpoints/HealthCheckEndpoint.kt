package net.corda.tools.error.codes.server.web.endpoints

import com.uchuhimo.konf.Config
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import javax.inject.Inject
import javax.inject.Named

@Named
internal class HealthCheckEndpoint @Inject constructor(configuration: HealthCheckEndpoint.Configuration) : ConfigurableEndpoint(configuration, setOf(HttpMethod.GET)) {

    override fun install(router: Router) {

        router.get(path).withDefaults().handler { ctx -> ctx.response().setStatusCode(HttpResponseStatus.OK.code()).end() }
    }

    interface Configuration : ConfigurableEndpoint.Configuration

    @Named
    internal class HealthCheckConfigProvider @Inject constructor(applyConfigStandards: (Config) -> Config) : HealthCheckEndpoint.Configuration, EndpointConfigProvider(CONFIGURATION_SECTION_PATH, applyConfigStandards) {

        private companion object {

            private const val CONFIGURATION_SECTION_PATH = "configuration.web.server.endpoints.healthcheck"
        }
    }
}
