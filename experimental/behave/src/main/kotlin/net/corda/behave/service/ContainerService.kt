/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.service

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import net.corda.behave.monitoring.PatternWatch
import rx.Observable
import java.io.Closeable

abstract class ContainerService(
        name: String,
        port: Int,
        val startupStatement: String,
        settings: ServiceSettings = ServiceSettings()
) : Service(name, port, settings), Closeable {

    protected val client: DockerClient = DefaultDockerClient.fromEnv().build()

    protected var id: String? = null

    protected open val baseImage: String = ""

    protected open val imageTag: String = "latest"

    protected abstract val internalPort: Int

    private var isClientOpen: Boolean = true

    private val environmentVariables: MutableList<String> = mutableListOf()

    private val imageReference: String
        get() = "$baseImage:$imageTag"

    override fun startService(): Boolean {
        return try {
            val port = "$internalPort"
            val portBindings = mapOf(
                    port to listOf(PortBinding.of("0.0.0.0", this.port))
            )
            val hostConfig = HostConfig.builder().portBindings(portBindings).build()
            val containerConfig = ContainerConfig.builder()
                    .hostConfig(hostConfig)
                    .image(imageReference)
                    .exposedPorts(port)
                    .env(*environmentVariables.toTypedArray())
                    .build()

            val creation = client.createContainer(containerConfig)
            id = creation.id()

            val info = client.inspectContainer(id)
            log.info("Container $id info: $info")

            client.startContainer(id)
            true
        } catch (e: Exception) {
            id = null
            e.printStackTrace()
            false
        }
    }

    override fun stopService(): Boolean {
        if (id != null) {
            client.stopContainer(id, 30)
            client.removeContainer(id)
            id = null
        }
        return true
    }

    protected fun addEnvironmentVariable(name: String, value: String) {
        environmentVariables.add("$name=$value")
    }

    override fun checkPrerequisites() {
        if (!client.listImages().any { true == it.repoTags()?.contains(imageReference) }) {
            log.info("Pulling image $imageReference ...")
            client.pull(imageReference, { _ ->
                run { }
            })
            log.info("Image $imageReference downloaded")
        }
    }

    override fun verify(): Boolean {
        return true
    }

    override fun waitUntilStarted(): Boolean {
        try {
            var timeout = settings.startupTimeout.toMillis()
            while (timeout > 0) {
                client.logs(id, DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr()).use {
                    val contents = it.readFully()
                    val observable = Observable.from(contents.split("\n", "\r"))
                    if (PatternWatch(observable, startupStatement).await(settings.pollInterval)) {
                        log.info("Found process start-up statement for {}", this)
                        return true
                    }
                }
                timeout -= settings.pollInterval.toMillis()
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    override fun close() {
        if (isClientOpen) {
            isClientOpen = false
            client.close()
        }
    }
}
