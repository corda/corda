package net.corda.webserver.api

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.utilities.NetworkHostAndPort
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
     * Report this node's addresses.
     */
    @GET
    @Path("addresses")
    @Produces(MediaType.APPLICATION_JSON)
    fun addresses(): List<NetworkHostAndPort>

    /**
     * Report this node's legal identities.
     */
    @GET
    @Path("identities")
    @Produces(MediaType.APPLICATION_JSON)
    fun identities(): List<Party>

    /**
     * Report this node's platform version.
     */
    @GET
    @Path("platformversion")
    @Produces(MediaType.APPLICATION_JSON)
    fun platformVersion(): Int

    /**
     * Report the peers on the network.
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun peers(): List<Party>

    /**
     * Report the notaries on the network.
     */
    @GET
    @Path("notaries")
    @Produces(MediaType.APPLICATION_JSON)
    fun notaries(): List<Party>

    /**
     * Report this node's registered flows.
     */
    @GET
    @Path("flows")
    @Produces(MediaType.APPLICATION_JSON)
    fun flows(): List<String>

    /**
     * Report this node's vault states.
     */
    @GET
    @Path("states")
    @Produces(MediaType.APPLICATION_JSON)
    fun states(): List<StateAndRef<ContractState>>
}