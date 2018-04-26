package net.corda.bootstrapper.backends

import net.corda.bootstrapper.backends.Backend.BackendType.AZURE
import net.corda.bootstrapper.backends.Backend.BackendType.LOCAL_DOCKER
import net.corda.bootstrapper.containers.instance.Instantiator
import net.corda.bootstrapper.containers.push.ContainerPusher
import net.corda.bootstrapper.context.Context
import net.corda.bootstrapper.volumes.Volume
import java.io.File

interface Backend {
    companion object {
        fun fromContext(context: Context, baseDir: File): Backend {
            return when (context.backendType) {
                AZURE -> AzureBackend.fromContext(context)
                LOCAL_DOCKER -> DockerBackend.fromContext(context, baseDir)
            }
        }
    }

    val containerPusher: ContainerPusher
    val instantiator: Instantiator
    val volume: Volume

    enum class BackendType {
        AZURE, LOCAL_DOCKER
    }

    operator fun component1(): ContainerPusher {
        return containerPusher
    }

    operator fun component2(): Instantiator {
        return instantiator
    }

    operator fun component3(): Volume {
        return volume
    }
}