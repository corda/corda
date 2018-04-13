package net.corda.behave.service.containers

import com.spotify.docker.client.DockerClient
import net.corda.behave.process.JarCommand
import net.corda.behave.service.ContainerService

class DockerContainerService(
        name: String = "Docker Container",
        port: Int = 12345,
        override val internalPort: Int,
        override val baseImage: String = "",
        override val imageTag: String = "latest",
        startupStatement: String

) : ContainerService(name, port, startupStatement) {

    init {
        log.info("DOCKER_HOST ${System.getenv("DOCKER_HOST")}")
        log.info("DOCKER_CERT_PATH ${System.getenv("DOCKER_CERT_PATH")}")

        addEnvironmentVariable("DOORMAN_SERVICE_HOST","localhost")
        addEnvironmentVariable("P2P_ADDRESS", "localhost:10001")
        addEnvironmentVariable("RPC_ADDRESS", "localhost:10002")
        addEnvironmentVariable("ADMIN_ADDRESS", "localhost:10003")
    }

    fun execute(command: JarCommand) : Boolean {
        val commandStrArray = arrayOf("/bin/sh", "entrypoint.sh")
        log.info("Docker container executing command {} ...", commandStrArray)
        return try {
            // command.command.toTypedArray()
//            val id = client.info().id()
            val execCreation = client.execCreate(id, commandStrArray,
                    DockerClient.ExecCreateParam.attachStdout(),
                    DockerClient.ExecCreateParam.attachStderr())
            val logStream = client.execStart(execCreation.id())
            //TODO Turn logStream into Observable for command
            true
        } catch (e: Exception) {
            log.warn("Failed to execute command {}", commandStrArray)
            e.printStackTrace()
            id = null
            false
        }
    }
}