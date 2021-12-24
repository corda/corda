package net.corda.node.internal.shell

import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.shell.determineUnsafeUsers
import net.corda.nodeapi.internal.cordapp.CordappLoader
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths

object InteractiveShell {

    private val log = LoggerFactory.getLogger(InteractiveShell::class.java)

    private const val SHELL_JAR_PATH = "drivers/corda-shell-4.8.jar"

    private const val INTERACTIVE_SHELL_CLASS = "net.corda.tools.shell.InteractiveShell"
    private const val CRASH_COMMAND_CLASS = "org.crsh.ssh.term.CRaSHCommand"

    private const val RUN_LOCAL_SHELL_METHOD = "runLocalShell"
    private const val SET_USER_INFO_METHOD = "setUserInfo"

    fun startShellIfInstalled(configuration: NodeConfiguration, shellConfiguration: Map<String, Any?>, cordappLoader: CordappLoader) {
        val uriToShellJar = Paths.get("${configuration.baseDirectory}/$SHELL_JAR_PATH").toUri()
        if (File(uriToShellJar).exists()) {
            try {
                // need to use the right release version?
                val classLoader = URLClassLoader(arrayOf(uriToShellJar.toURL()), javaClass.classLoader)
                setUnsafeUsers(classLoader, configuration)
                startShell(classLoader, shellConfiguration, cordappLoader)
            } catch (e: Exception) {
                log.error("Shell failed to start", e)
            }
        }
    }

    fun runLocalShellIfInstalled(baseDirectory: Path, onExit: () -> Unit = {}) {
        val uriToShellJar = Paths.get("$baseDirectory/$SHELL_JAR_PATH").toUri()
        if (File(uriToShellJar).exists()) {
            try {
                // need to use the right release version?
                val classLoader = URLClassLoader(arrayOf(uriToShellJar.toURL()), javaClass.classLoader)
                runLocalShell(classLoader, onExit)
            } catch (e: Exception) {
                log.error("Shell failed to start", e)
            }
        }
    }

    private fun setUnsafeUsers(classLoader: ClassLoader, configuration: NodeConfiguration) {
        val clazz1 = classLoader.loadClass(CRASH_COMMAND_CLASS)
        val unsafeUsers = determineUnsafeUsers(configuration)
        clazz1.getDeclaredMethod(SET_USER_INFO_METHOD, Set::class.java, Boolean::class.java, Boolean::class.java)
            .invoke(null, unsafeUsers, true, false)
        log.info("Setting unsafe users as: $unsafeUsers")
    }

    private fun startShell(classLoader: ClassLoader, shellConfiguration: Map<String, Any?>, cordappLoader: CordappLoader) {
        val clazz = classLoader.loadClass(INTERACTIVE_SHELL_CLASS)
        val instance = clazz.getDeclaredConstructor()
            .apply { this.isAccessible = true }
            .newInstance()
        clazz.getDeclaredMethod("startShell", Map::class.java, ClassLoader::class.java, Boolean::class.java)
            .invoke(instance, shellConfiguration, cordappLoader.appClassLoader, false)
        log.info("INTERACTIVE SHELL STARTED ABSTRACT NODE")
    }

    private fun runLocalShell(classLoader: ClassLoader, onExit: () -> Unit = {}) {
        val clazz = classLoader.loadClass(INTERACTIVE_SHELL_CLASS)
        val instance = clazz.getDeclaredConstructor()
            .apply { this.isAccessible = true }
            .newInstance()
        clazz.getDeclaredMethod(RUN_LOCAL_SHELL_METHOD, Function0::class.java).invoke(instance, onExit)
        log.info("INTERACTIVE SHELL STARTED")
    }
}