package net.corda.tools.error.codes.webserver

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication

// This is the default, but it's better explicit.
@SpringBootApplication(scanBasePackageClasses = [ErrorCodesWebApplication::class])
private open class ErrorCodesWebApplication

fun main(args: Array<String>) {

    val application = SpringApplication(ErrorCodesWebApplication::class.java)

    application.setBannerMode(Banner.Mode.OFF)
    application.webApplicationType = WebApplicationType.NONE

    application.run(*args).use {
        // To ensure it gets closed.
    }
}