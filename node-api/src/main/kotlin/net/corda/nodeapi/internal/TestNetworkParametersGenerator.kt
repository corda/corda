package net.corda.nodeapi.internal

import com.typesafe.config.ConfigFactory
import net.corda.cordform.CordformNode
import net.corda.cordform.NetworkParametersGenerator
import net.corda.core.crypto.SignedData
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.readAll
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.KryoHeaderV0_1
import java.nio.file.Path
import java.time.Instant
import kotlin.streams.toList

// This class is used by deployNodes task to generate NetworkParameters in [Cordformation].
@Suppress("UNUSED")
class TestNetworkParametersGenerator : NetworkParametersGenerator {
    companion object {
        private val logger = contextLogger()
    }

    override fun run(nodesDirs: List<Path>) {
        logger.info("NetworkParameters generation using node directories: $nodesDirs")
        try {
            initialiseSerialization()
            val notaryInfos = loadAndGatherNotaryIdentities(nodesDirs)
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
        } finally {
            _contextSerializationEnv.set(null)
        }
    }
    
    private fun loadAndGatherNotaryIdentities(nodesDirs: List<Path>): List<NotaryInfo> {
        val infos = getAllNodeInfos(nodesDirs)
        val configs = nodesDirs.map { ConfigFactory.parseFile((it / "node.conf").toFile()) }
        val notaryConfigs = configs.filter { it.hasPath("notary") }
        val notaries = notaryConfigs.associateBy(
                { CordaX500Name.parse(it.getString("myLegalName")) },
                { it.getConfig("notary").getBoolean("validating") }
        )
        // Now get the notary identities based on names passed from configs. There is one problem, for distributed notaries
        // in config we specify only node's main name, the notary identity isn't passed there. It's read from keystore on
        // node startup, so we have to look it up from node info as a second identity, which is ugly.
        return infos.mapNotNull {
            info -> notaries[info.legalIdentities[0].name]?.let { NotaryInfo(info.notaryIdentity(), it) }
        }.distinct()
    }

    /**
     * Loads latest NodeInfo files stored in node's base directory.
     * Scans main directory and [CordformNode.NODE_INFO_DIRECTORY].
     * Signatures are checked before returning a value. The latest value stored for a given name is returned.
     *
     * @return list of latest [NodeInfo]s
     */
    private fun getAllNodeInfos(nodesDirs: List<Path>): List<NodeInfo> {
        val nodeInfoFiles = nodesDirs.map { dir ->
            dir.list { it.filter { "nodeInfo-" in it.toString() }.findFirst().get() }
        }
        return nodeInfoFiles.mapNotNull { processFile(it) }
    }

    private fun processFile(file: Path): NodeInfo? {
        return try {
            logger.info("Reading NodeInfo from file: $file")
            val signedData = file.readAll().deserialize<SignedData<NodeInfo>>()
            signedData.verified()
        } catch (e: Exception) {
            logger.warn("Exception parsing NodeInfo from file. $file", e)
            null
        }
    }

    private fun NodeInfo.notaryIdentity() = if (legalIdentities.size == 2) legalIdentities[1] else legalIdentities[0]

    // We need to to set serialization env, because generation of parameters is run from Cordform.
    // KryoServerSerializationScheme is not accessible from nodeapi.
    private fun initialiseSerialization() {
        val context = if (java.lang.Boolean.getBoolean("net.corda.testing.amqp.enable")) AMQP_P2P_CONTEXT else KRYO_P2P_CONTEXT
        _contextSerializationEnv.set(SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(KryoParametersSerializationScheme)
                    registerScheme(AMQPServerSerializationScheme())
                },
                context))
    }

    private object KryoParametersSerializationScheme : AbstractKryoSerializationScheme() {
        override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
            return byteSequence == KryoHeaderV0_1 && target == SerializationContext.UseCase.P2P
        }

        override fun rpcClientKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
    }
}
