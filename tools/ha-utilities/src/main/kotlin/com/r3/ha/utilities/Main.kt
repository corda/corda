package com.r3.ha.utilities

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import org.slf4j.Logger
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths

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

fun Logger.logConfigPath(configPath: Path?) = configPath?.let { info("Reading ${configPath.toAbsolutePath().normalize()}") }
fun Logger.logCryptoServiceName(cryptoServiceName: SupportedCryptoServices?, legalName: CordaX500Name) =
        info("Using ${cryptoServiceName ?: SupportedCryptoServices.BC_SIMPLE} crypto service for: $legalName")

/**
 * Class which should be extended by implementations of sub-commands. Provides some common functionality, e.g. exception logging,
 * loading jars from 'drivers' folder etc.
 */
abstract class HAToolBase(alias: String, description: String) : CliWrapperBase(alias, description) {
    companion object {
        private val logger by lazy { contextLogger() }
    }

    /**
     * Base directory where to lookup `drivers` sub-folder in addition to current dir. Return `null` to skip loading jars from `drivers` folder.
     */
    abstract val driversParentDir: Path?

    /**
     * Should be used instead of [runProgram] to make sure that exceptions are properly logged.
     */
    abstract fun runTool()

    override fun runProgram(): Int {
        return try {
            if (driversParentDir != null && !HAUtilities.addJarsInDriversDirectoryToSystemClasspath(driversParentDir!!)) {
                HAUtilities.addJarsInDriversDirectoryToSystemClasspath(Paths.get("."))
            }
            runTool()
            ExitCodes.SUCCESS
        } catch (e: NoClassDefFoundError) {
            logger.error("Crypto service driver not found: please check that the 'drivers' directory contains the client side jar for the HSM", e)
            ExitCodes.FAILURE
        } catch (e: Throwable) {
            logger.error("${javaClass.simpleName} failed with exception", e)
            ExitCodes.FAILURE
        }
    }
}