package net.corda.core.utilities

import net.corda.core.crypto.Party
import net.corda.core.crypto.PublicKeyTree
import net.corda.core.node.ServiceHub
import javax.ws.rs.core.Response

/**
 * Utility functions to reduce boilerplate when developing HTTP APIs
 */
class ApiUtils(val services: ServiceHub) {
    private val defaultNotFound = { msg: String -> Response.status(Response.Status.NOT_FOUND).entity(msg).build() }

    /**
     * Get a party and then execute the passed function with the party public key as a parameter.
     * Usage: withParty(key) { doSomethingWith(it) }
     */
    fun withParty(partyKeyStr: String, notFound: (String) -> Response = defaultNotFound, found: (Party) -> Response): Response {
        return try {
            val partyKey = PublicKeyTree.parseFromBase58(partyKeyStr)
            val party = services.identityService.partyFromKey(partyKey)
            if(party == null) notFound("Unknown party") else found(party)
        } catch (e: IllegalArgumentException) {
            notFound("Invalid base58 key passed for party key")
        }
    }
}
