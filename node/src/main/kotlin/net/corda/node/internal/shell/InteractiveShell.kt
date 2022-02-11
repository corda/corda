package net.corda.node.internal.shell

import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.shell.determineUnsafeUsers
import net.corda.node.services.config.shell.toShellConfigMap
import net.corda.nodeapi.internal.cordapp.CordappLoader
import org.slf4j.LoggerFactory

object InteractiveShell {

    private val log = LoggerFactory.getLogger(InteractiveShell::class.java)

    private const val INTERACTIVE_SHELL_CLASS = "net.corda.tools.shell.InteractiveShell"
    private const val CRASH_COMMAND_CLASS = "org.crsh.ssh.term.CRaSHCommand"

    private const val START_SHELL_METHOD = "startShell"
    private const val RUN_LOCAL_SHELL_METHOD = "runLocalShell"
    private const val SET_USER_INFO_METHOD = "setUserInfo"

    fun startShellIfInstalled(configuration: NodeConfiguration, cordappLoader: CordappLoader): Boolean {
        return if (isShellInstalled()) {
            try {
                val shellConfiguration = configuration.toShellConfigMap()
                setUnsafeUsers(configuration)
                startShell(shellConfiguration, cordappLoader)
                true
            } catch (e: Exception) {
                log.error("Shell failed to start", e)
                false
            }
        } else {
            false
        }
    }

    /**
     * Only call this after [startShellIfInstalled] has been called or the required classes will not be loaded into the current classloader.
     */
    fun runLocalShellIfInstalled(onExit: () -> Unit = {}): Boolean {
        return if (isShellInstalled()) {
            try {
                runLocalShell(onExit)
                true
            } catch (e: Exception) {
                log.error("Shell failed to start", e)
                false
            }
        } else {
            false
        }
    }

    private fun isShellInstalled(): Boolean {
        return try {
            javaClass.classLoader.loadClass(INTERACTIVE_SHELL_CLASS)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun setUnsafeUsers(configuration: NodeConfiguration) {
        val unsafeUsers = determineUnsafeUsers(configuration)
        val clazz = javaClass.classLoader.loadClass(CRASH_COMMAND_CLASS)
        clazz.getDeclaredMethod(SET_USER_INFO_METHOD, Set::class.java, Boolean::class.java, Boolean::class.java)
            .invoke(null, unsafeUsers, true, false)
        log.info("Setting unsafe users as: $unsafeUsers")
    }

    private fun startShell(shellConfiguration: Map<String, Any?>, cordappLoader: CordappLoader) {
        val clazz = javaClass.classLoader.loadClass(INTERACTIVE_SHELL_CLASS)
        val instance = clazz.getDeclaredConstructor()
            .apply { this.isAccessible = true }
            .newInstance()
        clazz.getDeclaredMethod(START_SHELL_METHOD, Map::class.java, ClassLoader::class.java, Boolean::class.java)
            .invoke(instance, shellConfiguration, cordappLoader.appClassLoader, false)
    }

    private fun runLocalShell(onExit: () -> Unit = {}) {
        val clazz = javaClass.classLoader.loadClass(INTERACTIVE_SHELL_CLASS)
        // Gets the existing instance created by [startShell] as [InteractiveShell] is a static instance
        val instance = clazz.getDeclaredConstructor()
            .apply { this.isAccessible = true }
            .newInstance()
        clazz.getDeclaredMethod(RUN_LOCAL_SHELL_METHOD, Function0::class.java).invoke(instance, onExit)
    }
}