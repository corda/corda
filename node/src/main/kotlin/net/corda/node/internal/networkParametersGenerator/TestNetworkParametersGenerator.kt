@file:JvmName("NetworkParametersGenerator")

package net.corda.node.internal.networkParametersGenerator

import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.node.services.network.NodeInfoWatcher
import net.corda.nodeapi.internal.NetworkParametersCopier
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.testNetworkParameters
import java.nio.file.Path

// This class is used by deployNodes task to generate NetworkParameters in [Cordformation].
class TestNetworkParametersGenerator: NetworkParametersGenerator {
    fun run(baseDirectory: Path, notaryMap: Map<String, Boolean>) {
        initialiseSerialization()
        val notaryInfos = gatherNotaryInfos(baseDirectory, notaryMap)
        val copier = NetworkParametersCopier(testNetworkParameters(notaryInfos))
        copier.install(baseDirectory) // Put the parameters just to this one node directory, the rest copy in gradle.
    }

    private fun gatherNotaryInfos(baseDirectory: Path, notaries: Map<String, Boolean>): List<NotaryInfo> {
        val nodeInfoSerializer = NodeInfoWatcher(baseDirectory)
        return nodeInfoSerializer.loadAndGatherNotaryIdentities(notaries)
    }

    // TODO check if I need it
    private fun initialiseSerialization() {
        nodeSerializationEnv = SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(KryoServerSerializationScheme())
                    registerScheme(AMQPServerSerializationScheme())
                },
                KRYO_P2P_CONTEXT)
    }
}
