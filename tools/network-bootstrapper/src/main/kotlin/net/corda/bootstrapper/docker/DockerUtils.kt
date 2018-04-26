package net.corda.bootstrapper.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.DockerClientConfig

object DockerUtils {

    @Throws(Exception::class)
    fun createDockerClient(registryServerUrl: String, username: String, password: String): DockerClient {
        return DockerClientBuilder.getInstance(createDockerClientConfig(registryServerUrl, username, password))
                .build()
    }

    fun createLocalDockerClient(): DockerClient {
        return DockerClientBuilder.getInstance().build()
    }

    private fun createDockerClientConfig(registryServerUrl: String, username: String, password: String): DockerClientConfig {
        return DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerTlsVerify(false)
                .withRegistryUrl(registryServerUrl)
                .withRegistryUsername(username)
                .withRegistryPassword(password)
                .build()
    }


}