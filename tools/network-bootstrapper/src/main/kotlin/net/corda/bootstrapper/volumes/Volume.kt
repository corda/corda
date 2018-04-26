package net.corda.bootstrapper.volumes

import com.microsoft.azure.storage.file.CloudFile
import com.typesafe.config.ConfigFactory
import net.corda.bootstrapper.notaries.CopiedNotary
import net.corda.bootstrapper.serialization.SerializationEngine
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.deserialize
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.createDevNetworkMapCa
import java.io.File
import java.security.cert.X509Certificate
import java.time.Instant

interface Volume {
    fun notariesForNetworkParams(notaries: List<CopiedNotary>)

    companion object {
        init {
            SerializationEngine.init()
        }

        internal val networkMapCa = createDevNetworkMapCa(DEV_ROOT_CA)
        internal val networkMapCert: X509Certificate = networkMapCa.certificate
        internal val keyPair = networkMapCa.keyPair

    }


    fun CloudFile.uploadFromByteArray(array: ByteArray) {
        this.uploadFromByteArray(array, 0, array.size)
    }


    fun convertNodeIntoToNetworkParams(notaryFiles: List<Pair<File, File>>): NetworkParameters {
        val notaryInfos = notaryFiles.map { (configFile, nodeInfoFile) ->
            val validating = ConfigFactory.parseFile(configFile).getConfig("notary").getBoolean("validating")
            nodeInfoFile.readBytes().deserialize<SignedNodeInfo>().verified().let { NotaryInfo(it.legalIdentities.first(), validating) }
        }

        return notaryInfos.let {
            NetworkParameters(
                    minimumPlatformVersion = 1,
                    notaries = it,
                    maxMessageSize = 10485760,
                    maxTransactionSize = Int.MAX_VALUE,
                    modifiedTime = Instant.now(),
                    epoch = 10,
                    whitelistedContractImplementations = emptyMap())
        }
    }


}