package net.corda.shell

import joptsimple.OptionException
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.internal.*
import org.fusesource.jansi.AnsiConsole
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.streams.toList
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

private fun getCordappsInDirectory(cordappsDir: Path): List<URL> {
    return if (!cordappsDir.exists()) {
        emptyList()
    } else {
        cordappsDir.list {
            it.filter { it.isRegularFile() && it.toString().endsWith(".jar") }.map { it.toUri().toURL() }.toList()
        }
    }
}

fun main(args: Array<String>) {

    val argsParser = ArgsParser()
    val cmdlineOptions = try {
        argsParser.parse(*args)
    } catch (ex: OptionException) {
        println("Invalid command line arguments: ${ex.message}")
        argsParser.printHelp(System.out)
        exitProcess(1)
    }

    if (cmdlineOptions.help) {
        argsParser.printHelp(System.out)
        return
    }

    Tests(cmdlineOptions).run()
}

class Tests(private val cmdlineOptions: CmdLineOptions) {

    @Throws(IOException::class)
    private fun readLine(format: String, vararg args: Any): String {
        if (System.console() != null) {
            return System.console().readLine(format, *args)
        }
        print(String.format(format, *args))
        val reader = BufferedReader(InputStreamReader(System.`in`))
        return reader.readLine()
    }

    @Throws(IOException::class)
    private fun readPassword(format: String, vararg args: Any): CharArray {
        return if (System.console() != null) System.console().readPassword(format, *args) else this.readLine(format, *args).toCharArray()
    }

    fun login() : String {
        val passwordArray = readPassword("Password: ")
        return String(passwordArray)
    }

//    fun getVersionInfo(): VersionInfo {
//        // Manifest properties are only available if running from the corda jar
//        fun manifestValue(name: String): String? = if (Manifests.exists(name)) Manifests.read(name) else null
//
//        return VersionInfo(
//                manifestValue("Corda-Platform-Version")?.toInt() ?: 1,
//                manifestValue("Corda-Release-Version") ?: "Unknown",
//                manifestValue("Corda-Revision") ?: "Unknown",
//                manifestValue("Corda-Vendor") ?: "Unknown"
//        )
//    }

    fun run() {
        println("Console szsz ")
        println(System.console())
        val configuration = cmdlineOptions.toConfig()
        AnsiConsole.systemInstall()
        val runLocalShell = !configuration.noLocalShell

        val password: String? =
                if (!configuration.noLocalShell) {
//            val versionInfo = getVersionInfo()
//            println(Ansi.ansi().newline().fgBrightRed().a(
//                    """   ______               __""").newline().a(
//                    """  / ____/     _________/ /___ _""").newline().a(
//                    """ / /     __  / ___/ __  / __ `/         """).newline().fgBrightRed().a(
//                    """/ /___  /_/ / /  / /_/ / /_/ /          """).newline().fgBrightRed().a(
//                    """\____/     /_/   \__,_/\__,_/""").reset().newline().newline().fgBrightDefault().bold().
//                    a("--- ${versionInfo.vendor} ${versionInfo.releaseVersion} (${versionInfo.revision.take(7)}) -----------------------------------------------").
//                    newline().
//                    newline().
//                    reset())
                    login()
                } else {
                    null
                }

        val cordappJarPaths = getCordappsInDirectory(configuration.baseDirectory / "cordapps")
        val classLoader: ClassLoader = URLClassLoader(cordappJarPaths.toTypedArray(), javaClass.classLoader)

//      val client = CordaRPCClient(configuration.hostAndPort, sslConfiguration = configuration.ssl, classLoader = classLoader)
//      val rpcUser = User("demo" ?: "", "demo" ?: "", permissions = setOf(Permissions.all()))
//      val x = client.start(rpcUser.username, rpcUser.password)
        val latch = CountDownLatch(1)

        StandaloneShell.startShell(configuration,
                { username: String?, credentials: String? ->
                    val client = CordaRPCClient(configuration.hostAndPort, sslConfiguration = configuration.ssl, classLoader = classLoader)
                    client.start(username ?: "", credentials?: "").proxy
                }, classLoader, cmdlineOptions.user, password)

        if (runLocalShell) {
            try {
                StandaloneShell.connect()
            } catch(e: Exception) {
                println("Cannot login, reason ${e.cause?.message ?: e.message}")
                return
            }
            StandaloneShell.runLocalShell {
                latch.countDown()
            }
            latch.await()
        }
        exitProcess(0)
    }
}