package net.corda.node.webserver.api

import net.corda.core.node.NodeInfo
import java.time.LocalDateTime
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Top level interface to external interaction with the distributed ledger.
 *
 * Wherever a list is returned by a fetchXXX method that corresponds with an input list, that output list will have optional elements
 * where a null indicates "missing" and the elements returned will be in the order corresponding with the input list.
 *
 */
@Path("")
interface APIServer {
    /**
     * Report current UTC time as understood by the platform.
     */
    @GET
    @Path("servertime")
    @Produces(MediaType.APPLICATION_JSON)
    fun serverTime(): LocalDateTime

    /**
     * Report whether this node is started up or not.
     */
    @GET
    @Path("status")
    @Produces(MediaType.TEXT_PLAIN)
    fun status(): Response

    /**
     * Report this node's configuration and identities.
     */
    @GET
    @Path("info")
    @Produces(MediaType.APPLICATION_JSON)
    fun info(): NodeInfo
}