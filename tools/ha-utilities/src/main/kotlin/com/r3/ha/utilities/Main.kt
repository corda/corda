package com.r3.ha.utilities

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import net.corda.core.internal.div
import net.corda.core.internal.exists
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

fun main(args: Array<String>) {
    HAUtilities().start(args)
}

class HAUtilities : CordaCliWrapper("ha-utilities", "HA utilities contains tools to help setting up corda firewall services.") {
    override fun additionalSubCommands() = setOf(RegistrationTool(), BridgeSSLKeyTool(), InternalArtemisKeystoreGenerator(), InternalTunnelKeystoreGenerator(), ArtemisConfigurationTool())

    override fun runProgram(): Int {
        printHelp()
        return ExitCodes.FAILURE
    }

    companion object {
        fun addJarsInDriversDirectoryToSystemClasspath(baseDirectory: Path): Boolean {
            val driversDir: Path = baseDirectory / "drivers"
            if (driversDir.exists()) {
                driversDir.toFile().listFiles().filter { it.name.endsWith(".jar") }.forEach{ addToClasspath(it)}
                return true
            }
            else {
                return false
            }
        }

        private fun addToClasspath(file: File) {
            try {
                val url = file.toURI().toURL()
                val classLoader = ClassLoader.getSystemClassLoader() as URLClassLoader
                val method = URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java)
                method.isAccessible = true
                method.invoke(classLoader, url)
            } catch (e: Exception) {
                throw IllegalStateException("Unable to add to system class loader", e)
            }
        }
    }
}