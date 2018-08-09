/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.logging

import net.corda.core.context.Actor
import net.corda.core.context.InvocationContext
import net.corda.core.context.InvocationOrigin
import net.corda.core.context.Trace
import org.slf4j.MDC

internal fun InvocationContext.pushToLoggingContext() {

    trace.pushToLoggingContext()
    actor?.pushToLoggingContext()
    origin.pushToLoggingContext()
    externalTrace?.pushToLoggingContext("external_")
    impersonatedActor?.pushToLoggingContext("impersonating_")
}

internal fun Trace.pushToLoggingContext(prefix: String = "") {

    MDC.getMDCAdapter().apply {
        put("${prefix}invocation_id", invocationId.value)
        put("${prefix}invocation_timestamp", invocationId.timestamp.toString())
        put("${prefix}session_id", sessionId.value)
        put("${prefix}session_timestamp", sessionId.timestamp.toString())
    }
}

internal fun Actor.pushToLoggingContext(prefix: String = "") {

    MDC.getMDCAdapter().apply {
        put("${prefix}actor_id", id.value)
        put("${prefix}actor_store_id", serviceId.value)
        put("${prefix}actor_owning_identity", owningLegalIdentity.toString())
    }
}

internal fun InvocationOrigin.pushToLoggingContext(prefix: String = "") {

    MDC.getMDCAdapter().apply {
        put("${prefix}origin", principal().name)
    }
}