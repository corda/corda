package net.corda.verifier

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.ErrorOr
import net.corda.core.internal.addShutdownHook
import net.corda.core.div
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.ArtemisTcpTransport.Companion.tcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.VerifierApi
import net.corda.nodeapi.VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME
import net.corda.nodeapi.config.NodeSSLConfiguration
import net.corda.nodeapi.config.getValue
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import java.nio.file.Path
import java.nio.file.Paths

data class VerifierConfiguration(
        override val baseDirectory: Path,
        val config: Config
) : NodeSSLConfiguration {
    val nodeHostAndPort: HostAndPort by config
    override val keyStorePassword: String by config
    override val trustStorePassword: String by config
}

class Verifier {
    companion object {
        private val log = loggerFor<Verifier>()

        fun loadConfiguration(baseDirectory: Path, configPath: Path): VerifierConfiguration {
            val defaultConfig = ConfigFactory.parseResources("verifier-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))
            val customConfig = ConfigFactory.parseFile(configPath.toFile(), ConfigParseOptions.defaults().setAllowMissing(false))
            val resolvedConfig = customConfig.withFallback(defaultConfig).resolve()
            return VerifierConfiguration(baseDirectory, resolvedConfig)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            require(args.isNotEmpty()) { "Usage: <binary> BASE_DIR_CONTAINING_VERIFIER_CONF" }
            val baseDirectory = Paths.get(args[0])
            val verifierConfig = loadConfiguration(baseDirectory, baseDirectory / "verifier.conf")
            val locator = ActiveMQClient.createServerLocatorWithHA(
                    tcpTransport(ConnectionDirection.Outbound(), verifierConfig.nodeHostAndPort, verifierConfig)
            )
            val sessionFactory = locator.createSessionFactory()
            val session = sessionFactory.createSession(
                    VerifierApi.VERIFIER_USERNAME, VerifierApi.VERIFIER_USERNAME, false, true, true, locator.isPreAcknowledge, locator.ackBatchSize
            )
            addShutdownHook {
                log.info("Shutting down")
                session.close()
                sessionFactory.close()
            }
            val consumer = session.createConsumer(VERIFICATION_REQUESTS_QUEUE_NAME)
            val replyProducer = session.createProducer()
            consumer.setMessageHandler {
                val request = VerifierApi.VerificationRequest.fromClientMessage(it)
                log.debug { "Received verification request with id ${request.verificationId}" }
                val result = ErrorOr.catch {
                    request.transaction.verify()
                }
                if (result.error != null) {
                    log.debug { "Verification returned with error ${result.error}" }
                }
                val reply = session.createMessage(false)
                val response = VerifierApi.VerificationResponse(request.verificationId, result.error)
                response.writeToClientMessage(reply)
                replyProducer.send(request.responseAddress, reply)
                it.acknowledge()
            }
            session.start()
            log.info("Verifier started")
            Thread.sleep(Long.MAX_VALUE)
        }
    }
}