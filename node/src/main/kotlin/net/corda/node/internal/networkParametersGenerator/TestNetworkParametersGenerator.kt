package net.corda.node.internal.networkParametersGenerator

import net.corda.cordform.CordformNode
import net.corda.cordform.NetworkParametersGenerator
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.list
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.days
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.node.services.network.NodeInfoWatcher
import net.corda.nodeapi.internal.NetworkParametersCopier
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import java.nio.file.Path
import java.time.Instant
import kotlin.streams.toList

// This class is used by deployNodes task to generate NetworkParameters in [Cordformation].
class TestNetworkParametersGenerator : NetworkParametersGenerator {
    override fun run(notaryMap: Map<String, Boolean>, nodesDirs: List<Path>) {
        initialiseSerialization()
        val notaryInfos = loadAndGatherNotaryIdentities(nodesDirs[0], notaryMap.mapKeys { CordaX500Name.parse(it.key) })
        val copier = NetworkParametersCopier(NetworkParameters(
                minimumPlatformVersion = 1,
                notaries = notaryInfos,
                modifiedTime = Instant.now(),
                eventHorizon = 10000.days,
                maxMessageSize = 40000,
                maxTransactionSize = 40000,
                epoch = 1
        ))
        nodesDirs.forEach { copier.install(it) }
    }
    
    private fun loadAndGatherNotaryIdentities(baseDirectory: Path, notaries: Map<CordaX500Name, Boolean>): List<NotaryInfo> {
        val infos = getAllNodeInfos(baseDirectory)
        // Now get the notary identities based on names passed from Cordform configs. There is one problem, for distributed notaries
        // Cordfom specifies in config only node's main name, the notary identity isn't passed there. It's read from keystore on
        // node startup, so we have to look it up from node info as a second identity, which is ugly.
        // TODO Change Cordform definition so it specifies distributed notary identity.
        return infos.filter { it.legalIdentities[0].name in notaries.keys }.map {
            NotaryInfo(it.notaryIdentity(), notaries[it.legalIdentities[0].name]!!)
        }.distinct()
    }

    /**
     * Loads latest NodeInfo files stored in node's base directory.
     * Scans main directory and [CordformNode.NODE_INFO_DIRECTORY].
     * Signatures are checked before returning a value. The latest value stored for a given name is returned.
     *
     * @return list of latest [NodeInfo]s
     */
    private fun getAllNodeInfos(baseDirectory: Path): List<NodeInfo> {
        val infoWatcher = NodeInfoWatcher(baseDirectory)
        val nodeInfos = infoWatcher.loadFromDirectory()
        // NodeInfos are currently stored in 2 places: in [CordformNode.NODE_INFO_DIRECTORY] and in baseDirectory of the node.
        val myFiles = baseDirectory.list { it.filter { "nodeInfo-" in it.toString() }.toList() }
        val myNodeInfos = myFiles.mapNotNull { infoWatcher.processFile(it) }
        val infosMap = mutableMapOf<CordaX500Name, NodeInfo>()
        // Running deployNodes more than once produces new NodeInfos. We need to load the latest NodeInfos based on serial field.
        for (info in nodeInfos + myNodeInfos) {
            val name = info.legalIdentities.first().name
            val prevInfo = infosMap[name]
            if(prevInfo == null || prevInfo.serial < info.serial)
                infosMap.put(name, info)
        }
        return infosMap.values.toList()
    }

    private fun NodeInfo.notaryIdentity() = if (legalIdentities.size == 2) legalIdentities[1] else legalIdentities[0]

    private fun initialiseSerialization() {
        val context = if (java.lang.Boolean.getBoolean("net.corda.testing.amqp.enable")) AMQP_P2P_CONTEXT else KRYO_P2P_CONTEXT
        nodeSerializationEnv = SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(KryoServerSerializationScheme())
                    registerScheme(AMQPServerSerializationScheme())
                },
                context)
    }
}
