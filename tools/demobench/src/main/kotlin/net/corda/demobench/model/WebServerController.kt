package net.corda.demobench.model

import tornadofx.Controller
import java.nio.file.Path

class WebServerController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val cordaPath = jvm.applicationDir.resolve("corda").resolve("corda.jar")

    init {
        log.info("Web Server JAR: $cordaPath")
    }

    internal fun execute(cwd: Path) = jvm.execute(cordaPath, cwd, "--webserver")

    fun webServer() = WebServer(this)

}