package net.corda.demobench.model

import java.nio.file.Path
import java.nio.file.Paths
import tornadofx.Controller

class JVMConfig : Controller() {

    val userHome: Path = Paths.get(System.getProperty("user.home")).toAbsolutePath()
    val javaPath: Path = Paths.get(System.getProperty("java.home"), "bin", "java")
    val applicationDir: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()

    init {
        log.info("Java executable: $javaPath")
    }

    fun commandFor(jarPath: Path, vararg args: String): Array<String> {
        return arrayOf(javaPath.toString(), "-jar", jarPath.toString(), *args)
    }

    fun processFor(jarPath: Path, vararg args: String): ProcessBuilder {
        return ProcessBuilder(commandFor(jarPath, *args).toList())
    }

}

