package net.corda.services.messaging

import net.corda.core.internal.concurrent.openFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import org.apache.qpid.jms.JmsConnectionFactory
import org.apache.qpid.jms.meta.JmsConnectionInfo
import org.apache.qpid.jms.provider.Provider
import org.apache.qpid.jms.provider.ProviderFuture
import org.apache.qpid.jms.provider.amqp.AmqpProvider
import org.apache.qpid.jms.provider.amqp.AmqpSaslAuthenticator
import org.apache.qpid.jms.sasl.PlainMechanism
import org.apache.qpid.jms.transports.TransportOptions
import org.apache.qpid.jms.transports.netty.NettyTcpTransport
import org.apache.qpid.proton.engine.Sasl
import org.apache.qpid.proton.engine.SaslListener
import org.apache.qpid.proton.engine.Transport
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.jms.CompletionListener
import javax.jms.Connection
import javax.jms.Message
import javax.jms.MessageProducer
import javax.jms.Session
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Simple AMQP client connecting to broker using JMS.
 */
class SimpleAMQPClient(private val target: NetworkHostAndPort, private val config: MutualSslConfiguration) {
    companion object {
        /**
         * Send message and wait for completion.
         * @throws Exception on failure
         */
        fun MessageProducer.sendAndVerify(message: Message) {
            val request = openFuture<Unit>()
            send(message, object : CompletionListener {
                override fun onException(message: Message, exception: Exception) {
                    request.setException(exception)
                }

                override fun onCompletion(message: Message) {
                    request.set(Unit)
                }
            })
            try {
                request.get(10, TimeUnit.SECONDS)
            } catch (e: ExecutionException) {
                throw e.cause!!
            }
        }
    }

    private lateinit var connection: Connection

    private fun sslContext(): SSLContext {
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(config.keyStore.get().value.internal, config.keyStore.entryPassword.toCharArray())
        }
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(config.trustStore.get().value.internal)
        }
        val sslContext = SSLContext.getInstance("TLS")
        val keyManagers = keyManagerFactory.keyManagers
        val trustManagers = trustManagerFactory.trustManagers
        sslContext.init(keyManagers, trustManagers, SecureRandom())
        return sslContext
    }

    fun start(username: String, password: String): Session {
        val connectionFactory = TestJmsConnectionFactory("amqps://${target.host}:${target.port}", username, password)
        connectionFactory.setSslContext(sslContext())
        connection = connectionFactory.createConnection()
        connection.start()
        return connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    }

    fun stop() {
        try {
            connection.close()
        } catch (e: Exception) {
            // connection might not have initialised.
        }
    }

    private class TestJmsConnectionFactory(uri: String, private val user: String, private val pwd: String) : JmsConnectionFactory(uri) {
        override fun createProvider(remoteURI: URI): Provider {
            val transportOptions = TransportOptions().apply {
                // Disable SNI check for server certificate
                isVerifyHost = false
            }
            val transport = NettyTcpTransport(remoteURI, transportOptions, true)

            // Manually override SASL negotiations to accept failure in SASL-OUTCOME, which is produced by node Artemis server
            return object : AmqpProvider(remoteURI, transport) {
                override fun connect(connectionInfo: JmsConnectionInfo?) {
                    super.connect(connectionInfo)
                    val sasl = protonTransport.sasl()
                    sasl.client()
                    sasl.setRemoteHostname(remoteURI.host)
                    val authenticator = AmqpSaslAuthenticator {
                        PlainMechanism().apply {
                            username = user
                            password = pwd
                        }
                    }
                    val saslRequest = ProviderFuture()
                    sasl.setListener(object : SaslListener {
                        override fun onSaslMechanisms(sasl: Sasl, transport: Transport) {
                            authenticator.handleSaslMechanisms(sasl, transport)
                        }

                        override fun onSaslChallenge(sasl: Sasl, transport: Transport) {
                            authenticator.handleSaslChallenge(sasl, transport)
                        }

                        override fun onSaslOutcome(sasl: Sasl, transport: Transport) {
                            authenticator.handleSaslOutcome(sasl, transport)
                            saslRequest.onSuccess()
                        }

                        override fun onSaslInit(sasl: Sasl, transport: Transport) {
                        }

                        override fun onSaslResponse(sasl: Sasl, transport: Transport) {
                        }
                    })
                    pumpToProtonTransport()
                    saslRequest.sync()
                }
            }.apply {
                isSaslLayer = false
            }
        }
    }
}
