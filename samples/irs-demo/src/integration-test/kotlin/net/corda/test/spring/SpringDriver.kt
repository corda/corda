/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:Suppress("DEPRECATION")

package net.corda.test.spring

import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.map
import net.corda.core.utilities.contextLogger
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.WebserverHandle
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.node.internal.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

fun <A> springDriver(
        defaultParameters: DriverParameters = DriverParameters(),
        dsl: SpringBootDriverDSL.() -> A
): A {
    return genericDriver(
            defaultParameters = defaultParameters,
            driverDslWrapper = { driverDSL: DriverDSLImpl -> SpringBootDriverDSL(driverDSL) },
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
        val webReadyFuture = addressMustBeBoundFuture(driverDSL.executorService, (handle as NodeHandleInternal).webAddress, process)
        return webReadyFuture.map { queryWebserver(handle, process, checkUrl) }
    }

    private fun queryWebserver(handle: NodeHandle, process: Process, checkUrl: String): WebserverHandle {
        val protocol = if ((handle as NodeHandleInternal).useHTTPS) "https://" else "http://"
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
        return ProcessUtilities.startJavaProcess(
                className = clazz.canonicalName, // cannot directly get class for this, so just use string
                jdwpPort = debugPort,
                extraJvmArguments = listOf(
                        "-Dname=node-${handle.p2pAddress}-webserver",
                        "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}"
                        // Inherit from parent process
                ),
                workingDirectory = handle.baseDirectory,
                arguments = listOf(
                        "--base-directory", handle.baseDirectory.toString(),
                        "--server.port=${(handle as NodeHandleInternal).webAddress.port}",
                        "--corda.host=${handle.rpcAddress}",
                        "--corda.user=${handle.rpcUsers.first().username}",
                        "--corda.password=${handle.rpcUsers.first().password}"
                )
        )
    }
}
