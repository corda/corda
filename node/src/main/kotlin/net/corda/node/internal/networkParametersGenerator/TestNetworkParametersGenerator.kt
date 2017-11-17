@file:JvmName("NetworkParametersGenerator")

package net.corda.node.internal.networkParametersGenerator

import net.corda.cordform.NetworkParametersGenerator
import net.corda.core.identity.CordaX500Name
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
        ), false)
        nodesDirs.forEach { copier.install(it) }
    }
    
    private fun loadAndGatherNotaryIdentities(baseDirectory: Path, notaries: Map<CordaX500Name, Boolean>): List<NotaryInfo> {
        val infos = NodeInfoWatcher(baseDirectory).getAllNodeInfos()
        // Now get the notary identities based on names passed from Cordform configs. There is one problem, for distributed notaries
        // Cordfom specifies in config only node's main name, the notary identity isn't passed there. It's read from keystore on
        // node startup, so we have to look it up from node info as a second identity, which is ugly.
        // TODO Change Cordform definition so it specifies distributed notary identity.
        return infos.filter { it.legalIdentities[0].name in notaries.keys }.map {
            NotaryInfo(it.notaryIdentity(), notaries[it.legalIdentities[0].name]!!)
        }.distinct()
    }

    private fun NodeInfo.notaryIdentity() = if (legalIdentities.size == 2) legalIdentities[1] else legalIdentities[0]

    private fun initialiseSerialization() {
        val context = if (java.lang.Boolean.getBoolean("net.corda.testing.amqp.enable"))
            AMQP_P2P_CONTEXT
        else KRYO_P2P_CONTEXT
        nodeSerializationEnv = SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(KryoServerSerializationScheme())
                    registerScheme(AMQPServerSerializationScheme())
                },
                context)
    }
}
