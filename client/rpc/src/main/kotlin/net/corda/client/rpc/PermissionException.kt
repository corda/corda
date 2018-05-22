/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:Suppress("DEPRECATION")

package net.corda.client.rpc

import net.corda.core.CordaRuntimeException
import net.corda.core.ClientRelevantError
import net.corda.nodeapi.exceptions.RpcSerializableError

/**
 * Thrown to indicate that the calling user does not have permission for something they have requested (for example
 * calling a method).
 */
class PermissionException(message: String) : CordaRuntimeException(message), RpcSerializableError, ClientRelevantError