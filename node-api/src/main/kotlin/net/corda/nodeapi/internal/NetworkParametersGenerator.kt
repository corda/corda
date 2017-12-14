package net.corda.nodeapi.internal

import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.SignedData
import net.corda.core.identity.Party
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
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.KryoHeaderV0_1
import java.nio.file.Path
import java.time.Instant

/**
 * This class is loaded by Cordform using reflection to generate the network parameters. It is assumed that Cordform has
 * already asked each node to generate its node info file.
 */
@Suppress("UNUSED")
class NetworkParametersGenerator {
    companion object {
        private val logger = contextLogger()
    }

    fun run(nodesDirs: List<Path>) {
        logger.info("NetworkParameters generation using node directories: $nodesDirs")
        try {
            initialiseSerialization()
            val notaryInfos = gatherNotaryIdentities(nodesDirs)
            val copier = NetworkParametersCopier(NetworkParameters(
                    minimumPlatformVersion = 1,
                    notaries = notaryInfos,
                    modifiedTime = Instant.now(),
                    eventHorizon = 10000.days,
                    maxMessageSize = 40000,
                    maxTransactionSize = 40000,
                    epoch = 1
            ))
            nodesDirs.forEach(copier::install)
        } finally {
            _contextSerializationEnv.set(null)
        }
    }
    
    private fun gatherNotaryIdentities(nodesDirs: List<Path>): List<NotaryInfo> {
        return nodesDirs.mapNotNull { nodeDir ->
            val nodeConfig = ConfigFactory.parseFile((nodeDir / "node.conf").toFile())
            if (nodeConfig.hasPath("notary")) {
                val validating = nodeConfig.getConfig("notary").getBoolean("validating")
                val nodeInfoFile = nodeDir.list { paths -> paths.filter { it.fileName.toString().startsWith("nodeInfo-") }.findFirst().get() }
                processFile(nodeInfoFile)?.let { NotaryInfo(it.notaryIdentity(), validating) }
            } else {
                null
            }
        }.distinct() // We need distinct as nodes part of a distributed notary share the same notary identity
    }

    private fun NodeInfo.notaryIdentity(): Party {
        return when (legalIdentities.size) {
            // Single node notaries have just one identity like all other nodes. This identity is the notary identity
            1 -> legalIdentities[0]
            // Nodes which are part of a distributed notary have a second identity which is the composite identity of the
            // cluster and is shared by all the other members. This is the notary identity.
            2 -> legalIdentities[1]
            else -> throw IllegalArgumentException("Not sure how to get the notary identity in this scenerio: $this")
        }
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

    // We need to to set serialization env, because generation of parameters is run from Cordform.
    // KryoServerSerializationScheme is not accessible from nodeapi.
    private fun initialiseSerialization() {
        _contextSerializationEnv.set(SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(KryoParametersSerializationScheme)
                    registerScheme(AMQPServerSerializationScheme())
                },
                AMQP_P2P_CONTEXT)
        )
    }

    private object KryoParametersSerializationScheme : AbstractKryoSerializationScheme() {
        override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
            return byteSequence == KryoHeaderV0_1 && target == SerializationContext.UseCase.P2P
        }
        override fun rpcClientKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
    }
}
