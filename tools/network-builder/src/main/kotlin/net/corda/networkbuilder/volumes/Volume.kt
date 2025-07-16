package net.corda.networkbuilder.volumes

import com.azure.storage.file.share.ShareFileClient
import com.azure.storage.file.share.models.ShareFileUploadOptions
import com.typesafe.config.ConfigFactory
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.deserialize
import net.corda.networkbuilder.notaries.CopiedNotary
import net.corda.networkbuilder.serialization.SerializationEngine
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.config.getBooleanCaseInsensitive
import net.corda.nodeapi.internal.createDevNetworkMapCa
import java.io.ByteArrayInputStream
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

    fun ShareFileClient.uploadFromByteArray(array: ByteArray) {
        val inputStream = ByteArrayInputStream(array)
        this.create(array.size.toLong())
        this.uploadWithResponse(ShareFileUploadOptions(inputStream), null, null)
    }

    fun convertNodeIntoToNetworkParams(notaryFiles: List<Pair<File, File>>): NetworkParameters {
        val notaryInfos = notaryFiles.map { (configFile, nodeInfoFile) ->
            val validating = ConfigFactory.parseFile(configFile).getConfig("notary").getBooleanCaseInsensitive("validating")
            nodeInfoFile.readBytes().deserialize<SignedNodeInfo>().verified().let { NotaryInfo(it.legalIdentities.first(), validating) }
        }

        @Suppress("MagicNumber") // default config constants
        return notaryInfos.let {
            NetworkParameters(
                    minimumPlatformVersion = 1,
                    notaries = it,
                    maxMessageSize = 10485760,
                    maxTransactionSize = 10485760,
                    modifiedTime = Instant.now(),
                    epoch = 10,
                    whitelistedContractImplementations = emptyMap())
        }
    }
}