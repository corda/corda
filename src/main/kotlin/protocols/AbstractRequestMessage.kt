package protocols

import core.messaging.MessageRecipients

/**
 * Abstract superclass for request messages sent to services, which includes common
 * fields such as replyTo and replyToTopic.
 */
abstract class AbstractRequestMessage(val replyTo: MessageRecipients, val sessionID: Long?)