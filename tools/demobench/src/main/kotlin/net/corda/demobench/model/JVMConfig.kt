package net.corda.demobench.model

import com.jediterm.terminal.ui.UIUtil
import java.nio.file.Path
import java.nio.file.Paths
import tornadofx.Controller

class JVMConfig : Controller() {

    private val javaExe = if (UIUtil.isWindows) "java.exe" else "java"
    private val runtime: Runtime = Runtime.getRuntime()

    val javaPath: Path = Paths.get(System.getProperty("java.home"), "bin", javaExe)

    fun commandFor(jarPath: Path, vararg args: String): Array<String> {
        return arrayOf(javaPath.toString(), "-jar", jarPath.toString(), *args)
    }

    fun execute(jarPath: Path, vararg args: String): Process {
        return runtime.exec(commandFor(jarPath, *args))
    }

    init {
        log.info("Java executable: " + javaPath)
    }

}