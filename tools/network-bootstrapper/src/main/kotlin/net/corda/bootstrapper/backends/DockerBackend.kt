package net.corda.bootstrapper.backends

import net.corda.bootstrapper.containers.instance.docker.DockerInstantiator
import net.corda.bootstrapper.containers.push.docker.DockerContainerPusher
import net.corda.bootstrapper.context.Context
import net.corda.bootstrapper.volumes.docker.LocalVolume
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


