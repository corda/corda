package net.corda.tools.error.codes.server.web.endpoints

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.Router
import net.corda.tools.error.codes.server.commons.web.vertx.Endpoint
import javax.inject.Inject
import javax.inject.Named

@Named
internal class HealthCheckEndpoint @Inject constructor(configuration: HealthCheckEndpoint.Configuration) : Endpoint {

    // TODO sollecitom introduce a map-based functional Endpoint template
    override fun install(router: Router) {

        router.get(path).failureHandler { ctx -> ctx.failure().writeTo(ctx.response()) }.handler { ctx -> ctx.response().setStatusCode(HttpResponseStatus.OK.code()).end() }
    }

    override val path = configuration.path

    override val name = configuration.name

    override val methods: Set<HttpMethod> = setOf(HttpMethod.GET)

    // TODO sollecitom extend common
    interface Configuration {

        val name: String
        val path: String
    }

    private fun Throwable.writeTo(response: HttpServerResponse) {

        // TODO sollecitom move to supertype
        response.statusCode = HttpResponseStatus.INTERNAL_SERVER_ERROR.code()
        message?.let { response.write(it) }
        response.end()
    }

    @Named
    internal class ConfigProvider @Inject constructor(applyConfigStandards: (Config) -> Config) : HealthCheckEndpoint.Configuration {

        private companion object {

            private const val CONFIGURATION_SECTION_PATH = "configuration.web.server.endpoints.healthcheck"

            private object Spec : ConfigSpec(CONFIGURATION_SECTION_PATH) {

                val name by Spec.required<String>()
                val path by Spec.required<String>()
            }
        }

        private val config = applyConfigStandards.invoke(Config { addSpec(Spec) })

        // TODO sollecitom add validation
        override val name: String = config[Spec.name]
        override val path: String = config[Spec.path]
    }
}