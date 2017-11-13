@file:JvmName("NetworkParametersGenerator")

package net.corda.node.internal.networkParametersGenerator

import joptsimple.OptionParser
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
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
import java.nio.file.Paths

// This class is used by deployNodes task to generate NetworkParameters in [Cordformation].
class NetworkParametersGenerator {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            initialiseSerialization()
            require(args.isNotEmpty())
            val optionParser = OptionParser()
            val baseDirectoryArg = optionParser.accepts("base-directory").withRequiredArg()
            val notariesArg = optionParser.accepts("notaries").withRequiredArg()
            val optionSet = optionParser.parse(*args)
            require(optionSet.has(baseDirectoryArg)) { "No node's base directory provided" }
            val baseDirectory: Path = Paths.get(optionSet.valueOf(baseDirectoryArg))
            val notaries = if (!optionSet.has(notariesArg)) emptyMap() else parseNotaries(optionSet.valueOf(notariesArg))
            val notaryInfos = gatherNotaryInfos(baseDirectory, notaries)
            val copier = NetworkParametersCopier(testNetworkParameters(notaryInfos))
            copier.install(baseDirectory) // Put the parameters just to this one node directory, the rest copy in gradle.
        }

        // TODO Use JSON?
        // We need to know what are the notary node names and weather the notary is validating.
        private fun parseNotaries(option: String): Map<CordaX500Name, Boolean> {
            return option.split("#").map {
                val tuple = it.split(":")
                require(tuple.size == 2)
                Pair(CordaX500Name.parse(tuple[0]), tuple[1].toBoolean())
            }.toMap()
        }

        private fun gatherNotaryInfos(baseDirectory: Path, notaries: Map<CordaX500Name, Boolean>): List<NotaryInfo> {
            val nodeInfoSerializer = NodeInfoWatcher(baseDirectory)
            return nodeInfoSerializer.loadAndGatherNotaryIdentities(notaries)
        }

        private fun initialiseSerialization() {
            nodeSerializationEnv = SerializationEnvironmentImpl(
                    SerializationFactoryImpl().apply {
                        registerScheme(KryoServerSerializationScheme())
                        registerScheme(AMQPServerSerializationScheme())
                    },
                    KRYO_P2P_CONTEXT)
        }
    }
}
