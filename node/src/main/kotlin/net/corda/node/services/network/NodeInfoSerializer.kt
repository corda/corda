package net.corda.node.services.network

import net.corda.core.crypto.SignedData
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ByteSequence
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.Node
import java.io.File
import java.nio.file.Path
import java.security.PublicKey

object NodeInfoSerializer {

    const val NODE_INFO_FOLDER = "additional-node-infos"

    fun loadFromFile(file: File): NodeInfo {
        val signedData: SignedData<NodeInfo> = ByteSequence.of(file.readBytes()).deserialize()
        return signedData.verified()
    }

    fun saveToFile(path: Path, nodeInfo: NodeInfo, keyManager: KeyManagementService) {
        path.toFile().mkdirs()
        val sb: SerializedBytes<NodeInfo> = nodeInfo.serialize()
        val regSig = keyManager.sign(sb.bytes, nodeInfo.legalIdentities.first().owningKey)
        val sd: SignedData<NodeInfo> = SignedData(sb, regSig)
        //nodeInfo.
        val file: File = (path / ("nodeInfo-" + sd.hashCode().toString())).toFile()
        file.writeBytes(sd.serialize().bytes)
    }

}