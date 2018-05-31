package net.corda.bootstrapper.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.DockerClientConfig
import org.apache.commons.lang3.SystemUtils

object DockerUtils {

    @Throws(Exception::class)
    fun createDockerClient(registryServerUrl: String, username: String, password: String): DockerClient {
        return DockerClientBuilder.getInstance(createDockerClientConfig(registryServerUrl, username, password))
                .build()
    }

    fun createLocalDockerClient(): DockerClient {
        return if (SystemUtils.IS_OS_WINDOWS) {
            DockerClientBuilder.getInstance("tcp://127.0.0.1:2375").build()
        } else {
            DockerClientBuilder.getInstance().build()
        }
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