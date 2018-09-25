package net.corda.tools.error.codes.server.web

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
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

    companion object {

        val errorCode = ErrorCode("123jdazz")
        val location = ErrorDescriptionLocation.External(URI.create("https://thisisatest/boom"), errorCode)
    }

    @ComponentScan(basePackageClasses = [ErrorCodesWebApplication::class], excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ErrorCodesWebApplication::class]), ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [WebServer.Options::class]), ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [Adapter::class])])
    @SpringBootApplication
    internal open class Configuration {

        @Bean
        open fun webServerOptions(): WebServer.Options {

            // TODO sollecitom try port 0
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

                    return Mono.just(Optional.of(location))
                }
            }
        }
    }

    @Test
    fun found_location_is_returned_as_temporary_redirect() {

        // TODO sollecitom refactor not to create one instance for each test
        val vertx = Vertx.vertx()
        val latch = CountDownLatch(1)
        try {
            // TODO sollecitom perhaps consider a blocking web client...
            val client = WebClient.create(vertx, WebClientOptions().setDefaultHost("localhost").setDefaultPort(webServer.options.port.value))
            client.get("/errors/${errorCode.value}").followRedirects(false).send { call ->
                if (call.succeeded()) {
                    val response = call.result()
                    assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.TEMPORARY_REDIRECT.code())
                    assertThat(response.headers()[HttpHeaderNames.LOCATION]).isEqualTo(location.uri.toASCIIString())
                    latch.countDown()
                } else {
                    throw call.cause()
                }
            }
            latch.await()
            // TODO sollecitom use `use()`
            client.close()
        } finally {
            latch.countDown()
            vertx.close()
        }
    }
}