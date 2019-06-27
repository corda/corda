package net.corda.networkbuilder.backends

import net.corda.networkbuilder.containers.instance.docker.DockerInstantiator
import net.corda.networkbuilder.containers.push.docker.DockerContainerPusher
import net.corda.networkbuilder.context.Context
import net.corda.networkbuilder.volumes.docker.LocalVolume
import java.io.File

class DockerBackend(override val containerPusher: DockerContainerPusher,
                    override val instantiator: DockerInstantiator,
                    override val volume: LocalVolume) : Backend {

    companion object {
        fun fromContext(context: Context, baseDir: File): DockerBackend {
            val dockerContainerPusher = DockerContainerPusher()
            val localVolume = LocalVolume(baseDir, context)
            val dockerInstantiator = DockerInstantiator(localVolume, context)
            return DockerBackend(dockerContainerPusher, dockerInstantiator, localVolume)
        }
    }
}