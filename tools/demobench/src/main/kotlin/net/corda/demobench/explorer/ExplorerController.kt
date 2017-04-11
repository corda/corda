package net.corda.demobench.explorer

import net.corda.demobench.model.JVMConfig
import tornadofx.*

class ExplorerController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val explorerPath = jvm.applicationDir.resolve("explorer").resolve("node-explorer.jar")

    init {
        log.info("Explorer JAR: $explorerPath")
    }

    internal fun process(vararg args: String) = jvm.processFor(explorerPath, *args)

    fun explorer() = Explorer(this)

}
