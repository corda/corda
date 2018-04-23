/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.messaging

import net.corda.core.serialization.CordaSerializable

/** The interface for a group of message recipients (which may contain only one recipient) */
@CordaSerializable
interface MessageRecipients

/** A base class for the case of point-to-point messages */
interface SingleMessageRecipient : MessageRecipients

/** A base class for a set of recipients specifically identified by the sender. */
interface MessageRecipientGroup : MessageRecipients

/** A special base class for the set of all possible recipients, without having to identify who they all are. */
interface AllPossibleRecipients : MessageRecipients
