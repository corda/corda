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

import net.corda.core.context.InvocationContext
import org.slf4j.MDC

internal fun InvocationContext.pushToLoggingContext() {

    MDC.put("invocation_id", trace.invocationId.value)
    MDC.put("invocation_timestamp", trace.invocationId.timestamp.toString())
    MDC.put("session_id", trace.sessionId.value)
    MDC.put("session_timestamp", trace.sessionId.timestamp.toString())
    actor?.let {
        MDC.put("actor_id", it.id.value)
        MDC.put("actor_store_id", it.serviceId.value)
        MDC.put("actor_owningIdentity", it.owningLegalIdentity.toString())
    }
    externalTrace?.let {
        MDC.put("external_invocation_id", it.invocationId.value)
        MDC.put("external_invocation_timestamp", it.invocationId.timestamp.toString())
        MDC.put("external_session_id", it.sessionId.value)
        MDC.put("external_session_timestamp", it.sessionId.timestamp.toString())
    }
    impersonatedActor?.let {
        MDC.put("impersonating_actor_id", it.id.value)
        MDC.put("impersonating_actor_store_id", it.serviceId.value)
        MDC.put("impersonating_actor_owningIdentity", it.owningLegalIdentity.toString())
    }
}