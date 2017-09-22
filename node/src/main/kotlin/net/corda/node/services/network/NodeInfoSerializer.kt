package net.corda.node.services.network

import net.corda.core.crypto.SignedData
import net.corda.core.internal.div
import net.corda.core.internal.isDirectory
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.loggerFor
import java.io.File
import java.nio.file.Path

/**
 * Class containing the logic to serialize and de-serialize a [NodeInfo] to disk and reading them back.
 */
class NodeInfoSerializer {

    companion object {
        /**
         * Path relative to the running node where to look for serialized NodeInfos.
         * Keep this in sync with the value in Cordform.groovy.
         */
        const val NODE_INFO_FOLDER = "additional-node-infos"

        val logger = loggerFor<NodeInfoSerializer>()
    }

    /**
     * Saves the given [NodeInfo] to a path.
     * The node is 'encoded' as a SignedData<NodeInfo>, signed with the owning key of its first identity.
     * The name of the written file will be "nodeInfo-" followed by the hash of the content. The hash in the filename
     * is used so that one can freely copy these files without fearing to overwrite another one.
     *
     * @param path the path where to write the file, if non-existaent it will be created.
     * @param nodeInfo the NodeInfo to serialize.
     * @param keyManager a KeyManagementService used to sign the NodeInfo data.
     */
    fun saveToFile(path: Path, nodeInfo: NodeInfo, keyManager: KeyManagementService) {
        path.toFile().mkdirs()
        val serializedBytes: SerializedBytes<NodeInfo> = nodeInfo.serialize()
        val regSig = keyManager.sign(serializedBytes.bytes, nodeInfo.legalIdentities.first().owningKey)
        val signedData: SignedData<NodeInfo> = SignedData(serializedBytes, regSig)
        val file: File = (path / ("nodeInfo-" + signedData.hashCode().toString())).toFile()
        file.writeBytes(signedData.serialize().bytes)
    }

    /**
     * Loads all the files contained in a given path and returns the deserialized [NodeInfo]s.
     * Signatures are checked before returning a value.
     *
     * @param nodePath the node base path. NodeInfo files are searched for in nodePath/[NODE_INFO_FOLDER]
     * @return a list of [NodeInfo]s
     */
    fun loadFromDirectory(nodePath: Path): List<NodeInfo> {
        val result = mutableListOf<NodeInfo>()
        val nodeInfoDirectory = nodePath / NodeInfoSerializer.NODE_INFO_FOLDER
        if (!nodeInfoDirectory.isDirectory()) {
            logger.info("$nodeInfoDirectory isn't a Directory, not loading NodeInfo from files")
            return result
        }
        var readFiles = 0
        for (file in nodeInfoDirectory.toFile().walk().maxDepth(1)) if (file.isFile) {
            try {
                logger.info("Reading NodeInfo from file: $file")
                val nodeInfo = loadFromFile(file)
                result.add(nodeInfo)
                readFiles++
            } catch (e: Exception) {
                logger.error("Exception parsing NodeInfo from file. $file: " + e)
                e.printStackTrace()
            }
        }
        logger.info("Succesfully read $readFiles NodeInfo files.")
        return result
    }

    private fun loadFromFile(file: File): NodeInfo {
        val signedData: SignedData<NodeInfo> = ByteSequence.of(file.readBytes()).deserialize()
        return signedData.verified()
    }
}