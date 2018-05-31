package net.corda.bootstrapper.containers.instance.docker

import com.github.dockerjava.api.model.*
import net.corda.bootstrapper.Constants
import net.corda.bootstrapper.containers.instance.Instantiator
import net.corda.bootstrapper.context.Context
import net.corda.bootstrapper.docker.DockerUtils
import net.corda.bootstrapper.volumes.docker.LocalVolume
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture


class DockerInstantiator(private val volume: LocalVolume,
                         private val context: Context) : Instantiator {

    val networkId = setupNetwork();

    override fun instantiateContainer(imageId: String,
                                      portsToOpen: List<Int>,
                                      instanceName: String,
                                      env: Map<String, String>?): CompletableFuture<Pair<String, Map<Int, Int>>> {

        val localClient = DockerUtils.createLocalDockerClient()
        val convertedEnv = buildDockerEnv(env)
        val nodeInfosVolume = Volume(Instantiator.ADDITIONAL_NODE_INFOS_PATH)
        val existingContainers = localClient.listContainersCmd().withShowAll(true).exec()
                .map { it.names.first() to it }
                .filter { it.first.endsWith(instanceName) }
        existingContainers.forEach { (_, container) ->
            try {
                localClient.killContainerCmd(container.id).exec()
                LOG.info("Found running container: $instanceName killed")
            } catch (e: Throwable) {
                //container not running
            }
            try {
                localClient.removeContainerCmd(container.id).exec()
                LOG.info("Found existing container: $instanceName removed")
            } catch (e: Throwable) {
                //this *only* occurs of the container had been previously scheduled for removal
                //but did not complete before this attempt was begun.
            }

        }
        LOG.info("starting local docker instance of: $imageId with name $instanceName and env: $env")
        val ports = (portsToOpen + Constants.NODE_RPC_ADMIN_PORT).map { ExposedPort.tcp(it) }.map { PortBinding(null, it) }.let { Ports(*it.toTypedArray()) }
        val createCmd = localClient.createContainerCmd(imageId)
                .withName(instanceName)
                .withVolumes(nodeInfosVolume)
                .withBinds(Bind(volume.getPath(), nodeInfosVolume))
                .withPortBindings(ports)
                .withExposedPorts(ports.bindings.map { it.key })
                .withPublishAllPorts(true)
                .withNetworkMode(networkId)
                .withEnv(convertedEnv).exec()

        localClient.startContainerCmd(createCmd.id).exec()
        val foundContainer = localClient.listContainersCmd().exec()
                .filter { it.id == (createCmd.id) }
                .firstOrNull()

        val portMappings = foundContainer?.ports?.map {
            (it.privatePort ?: 0) to (it.publicPort ?: 0)
        }?.toMap()?.toMap()
                ?: portsToOpen.map { it to it }.toMap()



        return CompletableFuture.completedFuture(("localhost") to portMappings)
    }

    private fun buildDockerEnv(env: Map<String, String>?) =
            (env ?: emptyMap()).entries.map { (key, value) -> "$key=$value" }.toList()

    override fun getExpectedFQDN(instanceName: String): String {
        return instanceName
    }

    private fun setupNetwork(): String {
        val createLocalDockerClient = DockerUtils.createLocalDockerClient()
        val existingNetworks = createLocalDockerClient.listNetworksCmd().withNameFilter(context.safeNetworkName).exec()
        return if (existingNetworks.isNotEmpty()) {
            if (existingNetworks.size > 1) {
                throw IllegalStateException("Multiple local docker networks found with name ${context.safeNetworkName}")
            } else {
                LOG.info("Found existing network with name: ${context.safeNetworkName} reusing")
                existingNetworks.first().id
            }
        } else {
            val result = createLocalDockerClient.createNetworkCmd().withName(context.safeNetworkName).exec()
            LOG.info("Created local docker network: ${result.id} with name: ${context.safeNetworkName}")
            result.id
        }
    }

    companion object {
        val LOG = LoggerFactory.getLogger(DockerInstantiator::class.java)
    }
}