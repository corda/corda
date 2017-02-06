package net.corda.demobench.model

import tornadofx.Controller
import java.nio.file.Paths

class ExplorerController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val explorerPath = Paths.get("explorer", "node-explorer.jar").toAbsolutePath()

    init {
        log.info("Explorer JAR: " + explorerPath)
    }

    internal fun execute(vararg args: String) = jvm.execute(explorerPath, *args)

    fun explorer() = Explorer(this)

}
