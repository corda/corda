package com.r3.corda.networkmanage.doorman.webservice

import com.r3.corda.networkmanage.doorman.NetworkManagementServerStatus
import org.codehaus.jackson.map.ObjectMapper
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

class MonitoringWebService(private val serverStatus: NetworkManagementServerStatus) {
    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun status(): Response {
        return Response.ok(ObjectMapper().writeValueAsString(serverStatus)).build()
    }
}