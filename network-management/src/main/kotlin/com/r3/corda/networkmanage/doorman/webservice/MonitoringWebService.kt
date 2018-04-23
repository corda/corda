/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman.webservice

import com.r3.corda.networkmanage.doorman.NetworkManagementServerStatus
import org.codehaus.jackson.map.ObjectMapper
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/status")
class MonitoringWebService(private val serverStatus: NetworkManagementServerStatus) {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun status(): Response {
        return Response.ok(ObjectMapper().writeValueAsString(serverStatus)).build()
    }
}