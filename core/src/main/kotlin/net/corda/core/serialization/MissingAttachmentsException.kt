/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.serialization

import net.corda.core.CordaException
import net.corda.core.crypto.SecureHash

/** Thrown during deserialization to indicate that an attachment needed to construct the [WireTransaction] is not found. */
@CordaSerializable
class MissingAttachmentsException(val ids: List<SecureHash>) : CordaException()