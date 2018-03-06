/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.messaging

import net.corda.core.context.InvocationContext
import net.corda.node.internal.security.AuthorizingSubject

data class RpcAuthContext(val invocation: InvocationContext,
                          private val authorizer: AuthorizingSubject)
    : AuthorizingSubject by authorizer

