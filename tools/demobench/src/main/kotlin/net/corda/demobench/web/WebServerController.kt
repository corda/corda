/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.web

import net.corda.demobench.model.JVMConfig
import tornadofx.*

class WebServerController : Controller() {
    private val jvm by inject<JVMConfig>()
    private val webserverPath = jvm.applicationDir.resolve("corda").resolve("corda-webserver.jar")

    init {
        log.info("Web Server JAR: $webserverPath")
    }

    internal fun process() = jvm.processFor(webserverPath, "--config-file", "web-server.conf")

    fun webServer() = WebServer(this)
}
