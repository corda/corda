// This class is used by the smoke tests as a check that the node module isn't on their classpath
@file:JvmName("Corda")

package net.corda.node

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan("net.corda")
open class NodeStartupApp

fun main(args: Array<String>) {

    val application = SpringApplication(NodeStartupApp::class.java)

    application.setBannerMode(Banner.Mode.OFF)
    application.isWebEnvironment = false

    application.run(*args)
}