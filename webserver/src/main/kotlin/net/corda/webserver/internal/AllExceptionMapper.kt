/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.webserver.internal

import net.corda.core.utilities.loggerFor
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

// Provides basic exception logging to all APIs
@Provider
class AllExceptionMapper : ExceptionMapper<Exception> {
    companion object {
        private val logger = loggerFor<APIServerImpl>() // XXX: Really?
    }

    override fun toResponse(exception: Exception?): Response {
        logger.error("Unhandled exception in API", exception)
        return Response.status(500).build()
    }
}