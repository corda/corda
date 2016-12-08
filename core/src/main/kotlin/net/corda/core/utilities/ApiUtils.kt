package net.corda.core.utilities

import net.corda.core.ErrorOr
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.messaging.CordaRPCOps
import javax.ws.rs.core.Response

/**
 * Utility functions to reduce boilerplate when developing HTTP APIs
 */
class ApiUtils(val rpc: CordaRPCOps) {
    private val defaultNotFound = { msg: String -> Response.status(Response.Status.NOT_FOUND).entity(msg).build() }

    /**
     * Get a party and then execute the passed function with the party public key as a parameter.
     * Usage: withParty(key) { doSomethingWith(it) }
     */
    fun withParty(partyKeyStr: String, notFound: (String) -> Response = defaultNotFound, found: (Party) -> Response): Response {
        val party = try {
            val partyKey = CompositeKey.parseFromBase58(partyKeyStr)
            ErrorOr(rpc.partyFromKey(partyKey))
        } catch (e: IllegalArgumentException) {
            ErrorOr.of(Exception("Invalid base58 key passed for party key $e"))
        }
        return party.bind { if (it == null) ErrorOr.of(Exception("Unknown party")) else ErrorOr(found(it)) }.match(
                onValue = { it },
                onError = { notFound(it.toString()) }
        )
    }
}
