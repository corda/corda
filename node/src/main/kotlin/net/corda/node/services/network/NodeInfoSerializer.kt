package net.corda.node.services.network

import net.corda.cordform.CordformNode
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.isDirectory
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.loggerFor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Class containing the logic to serialize and de-serialize a [NodeInfo] to disk and reading it back.
 */
class NodeInfoSerializer {

    companion object {
        val logger = loggerFor<NodeInfoSerializer>()
    }

    /**
     * Saves the given [NodeInfo] to a path.
     * The node is 'encoded' as a SignedData<NodeInfo>, signed with the owning key of its first identity.
     * The name of the written file will be "nodeInfo-" followed by the hash of the content. The hash in the filename
     * is used so that one can freely copy these files without fearing to overwrite another one.
     *
     * @param path the path where to write the file, if non-existent it will be created.
     * @param nodeInfo the NodeInfo to serialize.
     * @param keyManager a KeyManagementService used to sign the NodeInfo data.
     */
    fun saveToFile(path: Path, nodeInfo: NodeInfo, keyManager: KeyManagementService) {
        try {
            path.createDirectories()
            val serializedBytes = nodeInfo.serialize()
            val regSig = keyManager.sign(serializedBytes.bytes, nodeInfo.legalIdentities.first().owningKey)
            val signedData = SignedData(serializedBytes, regSig)
            val file = (path / ("nodeInfo-" + SecureHash.sha256(serializedBytes.bytes).toString())).toFile()
            file.writeBytes(signedData.serialize().bytes)
        } catch (e : Exception) {
            logger.warn("Couldn't write node info to file: $e")
        }
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
        val nodeInfoDirectory = nodePath / CordformNode.NODE_INFO_DIRECTORY
        if (!nodeInfoDirectory.isDirectory()) {
            logger.info("$nodeInfoDirectory isn't a Directory, not loading NodeInfo from files")
            return result
        }
        for (path in Files.list(nodeInfoDirectory)) {
            val file = path.toFile()
            if (file.isFile) {
                try {
                    logger.info("Reading NodeInfo from file: $file")
                    val nodeInfo = loadFromFile(file)
                    result.add(nodeInfo)
                } catch (e: Exception) {
                    logger.error("Exception parsing NodeInfo from file. $file" , e)
                }
            }
        }
        logger.info("Succesfully read ${result.size} NodeInfo files.")
        return result
    }

    private fun loadFromFile(file: File): NodeInfo {
        val signedData = ByteSequence.of(file.readBytes()).deserialize<SignedData<NodeInfo>>()
        return signedData.verified()
    }
}