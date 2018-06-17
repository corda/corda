package net.corda.bootstrapper.volumes.docker

import net.corda.bootstrapper.context.Context
import net.corda.bootstrapper.notaries.CopiedNotary
import net.corda.bootstrapper.volumes.Volume
import net.corda.core.internal.signWithCert
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import org.slf4j.LoggerFactory
import java.io.File

class LocalVolume(scratchDir: File, context: Context) : Volume {

    private val networkDir = File(scratchDir, context.safeNetworkName)
    private val volumeDir = File(networkDir, "nodeinfos")
    private val networkParamsDir = File(volumeDir, "network-params")

    override fun notariesForNetworkParams(notaries: List<CopiedNotary>) {
        volumeDir.deleteRecursively()
        networkParamsDir.mkdirs()
        val networkParameters = convertNodeIntoToNetworkParams(notaries.map { it.configFile to it.nodeInfoFile })
        val networkParamsFile = File(networkParamsDir, NETWORK_PARAMS_FILE_NAME)
        networkParamsFile.outputStream().use { networkParameters.signWithCert(Volume.keyPair.private, Volume.networkMapCert).serialize().writeTo(it) }
        LOG.info("wrote network params to local file: ${networkParamsFile.absolutePath}")
    }


    fun getPath(): String {
        return volumeDir.absolutePath
    }

    companion object {
        val LOG = LoggerFactory.getLogger(LocalVolume::class.java)
    }
}