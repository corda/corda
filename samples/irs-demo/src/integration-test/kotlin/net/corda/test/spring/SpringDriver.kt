package net.corda.test.spring

import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.map
import net.corda.core.utilities.contextLogger
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.WebserverHandle
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

fun <A> springDriver(
        defaultParameters: DriverParameters = DriverParameters(),
        isDebug: Boolean = defaultParameters.isDebug,
        driverDirectory: Path = defaultParameters.driverDirectory,
        portAllocation: PortAllocation = defaultParameters.portAllocation,
        debugPortAllocation: PortAllocation = defaultParameters.debugPortAllocation,
        systemProperties: Map<String, String> = defaultParameters.systemProperties,
        useTestClock: Boolean = defaultParameters.useTestClock,
        initialiseSerialization: Boolean = defaultParameters.initialiseSerialization,
        startNodesInProcess: Boolean = defaultParameters.startNodesInProcess,
        notarySpecs: List<NotarySpec> = defaultParameters.notarySpecs,
        extraCordappPackagesToScan: List<String> = defaultParameters.extraCordappPackagesToScan,
        maxTransactionSize: Int = defaultParameters.maxTransactionSize,
        dsl: SpringBootDriverDSL.() -> A
): A {
    return genericDriver(
            defaultParameters = defaultParameters,
            isDebug = isDebug,
            driverDirectory = driverDirectory,
            portAllocation = portAllocation,
            debugPortAllocation = debugPortAllocation,
            systemProperties = systemProperties,
            useTestClock = useTestClock,
            initialiseSerialization = initialiseSerialization,
            startNodesInProcess = startNodesInProcess,
            extraCordappPackagesToScan = extraCordappPackagesToScan,
            notarySpecs = notarySpecs,
            driverDslWrapper = { driverDSL: DriverDSLImpl -> SpringBootDriverDSL(driverDSL) },
            maxTransactionSize = maxTransactionSize,
            coerce = { it }, dsl = dsl
    )
}

data class SpringBootDriverDSL(private val driverDSL: DriverDSLImpl) : InternalDriverDSL by driverDSL {
    companion object {
        private val log = contextLogger()
    }

    /**
     * Starts a Spring Boot application, passes the RPC connection data as parameters the process.
     * Returns future which will complete after (and if) the server passes healthcheck.
     * @param clazz Class with main method which is expected to run Spring application
     * @param handle Corda Node handle this webapp is expected to connect to
     * @param checkUrl URL path to use for server readiness check - uses [okhttp3.Response.isSuccessful] as qualifier
     *
     * TODO:  Rather then expecting a given clazz to contain main method which start Spring app our own simple class can do this
     */
    fun startSpringBootWebapp(clazz: Class<*>, handle: NodeHandle, checkUrl: String): CordaFuture<WebserverHandle> {
        val debugPort = if (driverDSL.isDebug) driverDSL.debugPortAllocation.nextPort() else null
        val process = startApplication(handle, debugPort, clazz)
        driverDSL.shutdownManager.registerProcessShutdown(process)
        val webReadyFuture = addressMustBeBoundFuture(driverDSL.executorService, handle.webAddress, process)
        return webReadyFuture.map { queryWebserver(handle, process, checkUrl) }
    }

    private fun queryWebserver(handle: NodeHandle, process: Process, checkUrl: String): WebserverHandle {
        val protocol = if (handle.useHTTPS) "https://" else "http://"
        val url = URL(URL("$protocol${handle.webAddress}"), checkUrl)
        val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

        var maxRetries = 30

        while (process.isAlive && maxRetries > 0) try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (response.isSuccessful) {
                    return WebserverHandle(handle.webAddress, process)
                }
            }

            TimeUnit.SECONDS.sleep(2)
            maxRetries--
        } catch (e: ConnectException) {
            log.debug("Retrying webserver info at ${handle.webAddress}")
        }

        throw IllegalStateException("Webserver at ${handle.webAddress} has died or was not reachable at URL $url")
    }

    private fun startApplication(handle: NodeHandle, debugPort: Int?, clazz: Class<*>): Process {
        val className = clazz.canonicalName
        return ProcessUtilities.startJavaProcessImpl(
                className = className, // cannot directly get class for this, so just use string
                jdwpPort = debugPort,
                extraJvmArguments = listOf(
                        "-Dname=node-${handle.configuration.p2pAddress}-webserver",
                        "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}"
                        // Inherit from parent process
                ),
                classpath = ProcessUtilities.defaultClassPath,
                workingDirectory = handle.configuration.baseDirectory,
                errorLogPath = Paths.get("error.$className.log"),
                arguments = listOf(
                        "--base-directory", handle.configuration.baseDirectory.toString(),
                        "--server.port=${handle.webAddress.port}",
                        "--corda.host=${handle.configuration.rpcOptions.address}",
                        "--corda.user=${handle.configuration.rpcUsers.first().username}",
                        "--corda.password=${handle.configuration.rpcUsers.first().password}"
                ),
                maximumHeapSize = null
        )
    }
}