package net.corda.shell

import com.jcabi.manifests.Manifests
import joptsimple.OptionException
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.internal.*
import org.fusesource.jansi.Ansi
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

    private fun getManifestEntry(key: String) = if (Manifests.exists(key)) Manifests.read(key) else "Unknown"

    fun run() {
        val configuration = cmdlineOptions.toConfig()
        AnsiConsole.systemInstall()
        println(Ansi.ansi().fgBrightRed().a(
                """   ______               __""").newline().a(
                """  / ____/     _________/ /___ _""").newline().a(
                """ / /     __  / ___/ __  / __ `/         """).newline().fgBrightRed().a(
                """/ /___  /_/ / /  / /_/ / /_/ /          """).newline().fgBrightRed().a(
                """\____/     /_/   \__,_/\__,_/""").reset().newline().fgBrightDefault().bold().
                a("--- ${getManifestEntry("Corda-Vendor")} ${getManifestEntry("Corda-Release-Version")} (${getManifestEntry("Corda-Revision").take(7)}) ---").
                newline().
                reset())
        val runLocalShell = !configuration.noLocalShell
        val password: String? = if (runLocalShell) {
                    login()
                } else {
                    println("Running in non local access - connect via SSH")
                    null
                }

        val cordappJarPaths = getCordappsInDirectory(configuration.baseDirectory / "cordapps")
        val classLoader: ClassLoader = URLClassLoader(cordappJarPaths.toTypedArray(), javaClass.classLoader)

        val exit = CountDownLatch(1)

        InteractiveShell.startShell(configuration,
                { username: String?, credentials: String? ->
                    val client = CordaRPCClient(configuration.hostAndPort, sslConfiguration = configuration.ssl, classLoader = classLoader)
                    client.start(username ?: "", credentials?: "").proxy
                }, classLoader, cmdlineOptions.user, password)

        if (configuration.sshd != null) {
            println("SSH server listening on port " + configuration.sshd!!.toString())
        }

        if (runLocalShell) {
            try {
                InteractiveShell.connect()
            } catch(e: Exception) {
                println("Cannot login, reason ${e.cause?.message ?: e.message}")
                return
            }
            InteractiveShell.runLocalShell {
                exit.countDown()
            }
        }
        exit.await()
        exitProcess(0)
    }
}