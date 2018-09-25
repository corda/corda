package net.corda.tools.error.codes.server.web

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import net.corda.tools.error.codes.server.ErrorCodesWebApplication
import net.corda.tools.error.codes.server.commons.web.Port
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.annotations.Adapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit.jupiter.SpringJUnitJupiterConfig
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.inject.Inject

@SpringJUnitJupiterConfig(PresentLocationContractTest.Configuration::class)
internal class PresentLocationContractTest {

    @Inject
    private lateinit var webServer: WebServer

    // TODO sollecitom try to see whether creating the application context manually, including starting it and stopping it, removes the need for static variables and `@DirtiesContext` annotations.
    private companion object {

        private var errorCode: ErrorCode? = null
        private var location: Mono<Optional<out ErrorDescriptionLocation>>? = null
    }

    @Test
    @DirtiesContext
    fun found_location_is_returned_as_temporary_redirect() {

        val errorCode = ErrorCode("123jdazz")
        val location = ErrorDescriptionLocation.External(URI.create("https://thisisatest/boom"), errorCode)
        checkContract(errorCode, Mono.just(Optional.of(location))) { response ->

            assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.TEMPORARY_REDIRECT.code())
            assertThat(response.headers()[HttpHeaderNames.LOCATION]).isEqualTo(location.uri.toASCIIString())
        }
    }

    @Test
    @DirtiesContext
    fun absent_location_results_in_not_found() {

        val errorCode = ErrorCode("123jdazz")
        checkContract(errorCode, Mono.empty()) { response ->

            assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.NOT_FOUND.code())
            assertThat(response.headers()[HttpHeaderNames.LOCATION]).isNull()
        }
    }

    private fun checkContract(errorCodeForServer: ErrorCode, locationReturned: Mono<Optional<out ErrorDescriptionLocation>>, assertResponse: (HttpResponse<Buffer>) -> Unit) {

        val vertx = Vertx.vertx()
        val latch = CountDownLatch(1)
        errorCode = errorCodeForServer
        location = locationReturned
        try {
            // TODO sollecitom perhaps consider a blocking web client...
            val client = WebClient.create(vertx, WebClientOptions().setDefaultHost("localhost").setDefaultPort(webServer.options.port.value))
            client.get("/errors/${errorCodeForServer.value}").followRedirects(false).send { call ->
                if (call.succeeded()) {
                    val response = call.result()
                    assertResponse.invoke(response)
                    latch.countDown()
                } else {
                    // TODO sollecitom check this.
                    throw call.cause()
                }
            }
            latch.await()
            // TODO sollecitom use `use()` here
            client.close()
        } finally {
            latch.countDown()
            vertx.close()
        }
    }

    @ComponentScan(basePackageClasses = [ErrorCodesWebApplication::class], excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ErrorCodesWebApplication::class]), ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [WebServer.Options::class]), ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [Adapter::class])])
    @SpringBootApplication
    internal open class Configuration {

        @Bean
        open fun webServerOptions(): WebServer.Options {

            return ServerSocket().use {

                it.reuseAddress = true
                it.bind(InetSocketAddress(0))
                object : WebServer.Options {

                    override val port = Port(it.localPort)
                }
            }
        }

        @Adapter
        @Bean
        open fun repository(): (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> {

            return object : (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> {

                override fun invoke(p1: ErrorCode, p2: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>> {

                    return location!!
                }
            }
        }
    }
}