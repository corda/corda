package net.corda.node.internal.artemis

import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.Message
import org.apache.activemq.artemis.core.server.ServerSession
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin
import org.apache.activemq.artemis.core.transaction.Transaction
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage

/**
 * Plugin to verify the user in the AMQP message header against the user in the authenticated session.
 *
 * In core protocol, Artemis Server automatically overwrites the _AMQ_VALIDATED_USER field in message header according to authentication
 * of the session. However, this is not done for AMQP protocol, which is used by Corda. Hence, _AMQ_VALIDATED_USER in AMQP packet is
 * delivered in the same form, as it was produced by counterpart. To prevent manipulations of this field by other peers, we should check
 * message header against user in authenticated session.
 *
 * Note that AMQP message is immutable, so changing the header means rebuilding the whole message, which is expensive. Instead, the
 * preferred option is to throw an exception.
 */
class UserValidationPlugin : ActiveMQServerPlugin {
    companion object {
        private val log = contextLogger()
    }

    override fun beforeSend(session: ServerSession, tx: Transaction?, message: Message, direct: Boolean, noAutoCreateQueue: Boolean) {
        try {
            if (session.username == PEER_USER) {
                if (message !is AMQPMessage) {
                    throw ActiveMQSecurityException("Invalid message type: expected [${AMQPMessage::class.java.name}], got [${message.javaClass.name}]")
                }
                val user = message.getStringProperty(Message.HDR_VALIDATED_USER)
                if (user != null && user != session.validatedUser) {
                    throw ActiveMQSecurityException("_AMQ_VALIDATED_USER mismatch: expected [${session.validatedUser}], got [${user}]")
                }
            }
        } catch (e: ActiveMQSecurityException) {
            throw e
        } catch (e: Throwable) {
            // Artemis swallows any exception except ActiveMQException
            log.error("Message validation failed", e)
            throw ActiveMQSecurityException("Message validation failed")
        }
    }
}