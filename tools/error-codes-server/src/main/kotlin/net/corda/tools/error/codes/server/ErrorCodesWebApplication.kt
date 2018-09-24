package net.corda.tools.error.codes.server

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication

// TODO sollecitom refactor the module to use sub-modules instead of packages.
// This is the default, but it's better explicit.
@SpringBootApplication(scanBasePackageClasses = [ErrorCodesWebApplication::class])
private open class ErrorCodesWebApplication

fun main(args: Array<String>) {

    val application = SpringApplication(ErrorCodesWebApplication::class.java)

    application.setBannerMode(Banner.Mode.OFF)
    application.webApplicationType = WebApplicationType.NONE

    application.run(*args)
}