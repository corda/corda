package net.corda.node

import net.corda.core.utilities.loggerFor
import net.corda.node.internal.ExampleService
import net.corda.node.internal.LoggingExampleService
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class SpringConfiguration {

    companion object {
        val logger = loggerFor<LoggingExampleService>()
    }

    // This could be a Mock
    @Bean
    open fun exampleService() : ExampleService = object : ExampleService {

        override fun doStuff() {
            logger.info("||TEST|| : DOING INCREDIBLY INTERESTING STUFF HERE!")
        }
    }
}

fun main(args: Array<String>) {

    val application = SpringApplication(NodeStartupApp::class.java)

    application.setBannerMode(Banner.Mode.OFF)
    application.isWebEnvironment = false

    application.run(*args)
}