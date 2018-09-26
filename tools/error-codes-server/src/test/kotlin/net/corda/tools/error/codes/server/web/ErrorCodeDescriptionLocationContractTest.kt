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
import reactor.core.publisher.Mono.empty
import reactor.core.publisher.Mono.just
import reactor.core.publisher.MonoProcessor
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.util.*
import javax.inject.Inject

@SpringJUnitJupiterConfig(ErrorCodeDescriptionLocationContractTest.Configuration::class)
internal class ErrorCodeDescriptionLocationContractTest {

    @Inject
    private lateinit var webServer: WebServer

    private companion object {

        private var errorCode: ErrorCode? = null
        private var location: Mono<Optional<out ErrorDescriptionLocation>>? = null
    }

    @Test
    @DirtiesContext
    fun found_location_is_returned_as_temporary_redirect() {

        val errorCode = ErrorCode("123jdazz")
        val location = ErrorDescriptionLocation.External(URI.create("https://thisisatest/boom"), errorCode)

        val response = performRequestWithStubbedValue(errorCode, just(Optional.of(location))).block()!!

        assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.TEMPORARY_REDIRECT.code())
        assertThat(response.headers()[HttpHeaderNames.LOCATION]).isEqualTo(location.uri.toASCIIString())
    }

    @Test
    @DirtiesContext
    fun absent_location_results_in_not_found() {

        val errorCode = ErrorCode("123jdazz")

        val response = performRequestWithStubbedValue(errorCode, empty()).block()!!

        assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.NOT_FOUND.code())
        assertThat(response.headers()[HttpHeaderNames.LOCATION]).isNull()
    }

    private fun performRequestWithStubbedValue(errorCodeForServer: ErrorCode, locationReturned: Mono<Optional<out ErrorDescriptionLocation>>): Mono<HttpResponse<Buffer>> {

        val vertx = Vertx.vertx()
        errorCode = errorCodeForServer
        location = locationReturned
        val promise = MonoProcessor.create<HttpResponse<Buffer>>()

        val client = WebClient.create(vertx, WebClientOptions().setDefaultHost("localhost").setDefaultPort(webServer.options.port.value))
        client.get("/errors/${errorCodeForServer.value}").followRedirects(false).send { call ->
            if (call.succeeded()) {
                promise.onNext(call.result())
                // TODO sollecitom use `use()` here
                client.close()
                vertx.close()
            } else {
                promise.onError(call.cause())
                // TODO sollecitom use `use()` here
                client.close()
                vertx.close()
            }
        }
        return promise
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