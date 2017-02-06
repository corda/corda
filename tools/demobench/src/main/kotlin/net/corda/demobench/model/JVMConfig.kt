package net.corda.demobench.model

import java.nio.file.Path
import java.nio.file.Paths
import tornadofx.Controller

class JVMConfig : Controller() {

    val javaPath: Path = Paths.get(System.getProperty("java.home"), "bin", "java")

    init {
        log.info("Java executable: " + javaPath)
    }

    fun commandFor(jarPath: Path, vararg args: String): Array<String> {
        return arrayOf(javaPath.toString(), "-jar", jarPath.toString(), *args)
    }

    fun execute(jarPath: Path, vararg args: String): Process {
        return Runtime.getRuntime().exec(commandFor(jarPath, *args))
    }

}