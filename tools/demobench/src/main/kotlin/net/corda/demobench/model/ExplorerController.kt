package net.corda.demobench.model

import tornadofx.Controller
import java.nio.file.Path
import java.nio.file.Paths

class ExplorerController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val explorerPath = jvm.applicationDir.resolve("explorer").resolve("node-explorer.jar")

    init {
        log.info("Explorer JAR: $explorerPath")
    }

    internal fun execute(cwd: Path, vararg args: String) = jvm.execute(explorerPath, cwd, *args)

    fun explorer() = Explorer(this)

}
