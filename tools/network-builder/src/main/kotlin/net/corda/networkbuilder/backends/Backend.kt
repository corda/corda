package net.corda.networkbuilder.backends

import net.corda.networkbuilder.backends.Backend.BackendType.AZURE
import net.corda.networkbuilder.backends.Backend.BackendType.LOCAL_DOCKER
import net.corda.networkbuilder.containers.instance.Instantiator
import net.corda.networkbuilder.containers.push.ContainerPusher
import net.corda.networkbuilder.context.Context
import net.corda.networkbuilder.volumes.Volume
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

    enum class BackendType(val displayName: String) {

        AZURE("Azure Containers"), LOCAL_DOCKER("Local Docker");

        override fun toString(): String {
            return this.displayName
        }
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