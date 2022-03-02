package net.corda.core.conclave.common

import java.security.MessageDigest
import java.util.*

/**
 * Parse [routingHint] of the format flowId:route and return a pair of flowId [String] and [EnclaveCommand] class.
 * @param routingHint the routing hint from receiveMail to parse.
 * @return a pair of flowId [String] and [EnclaveCommand].
 * @throws [IllegalArgumentException] if the hint cannot be parsed, or if the enclave command is not known.
 */
fun getRoutingInfo(routingHint: String): Pair<String, EnclaveCommand> {
    val routingElements = routingHint.split(":")
    if (routingElements.size != 2) {
        throw IllegalArgumentException("Expected routingHint in the form flowId:route")
    }

    val (flowId, routeName) = routingElements
    try {
        UUID.fromString(flowId)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid flowId: $flowId")
    }

    val route = try {
        routeName.trim().toEnclaveCommand()
    } catch (e: IllegalArgumentException) {
        throw java.lang.IllegalArgumentException("Unknown command: $routeName")
    }

    return Pair(flowId, route)
}

/**
 * Return the hash of the input [String] using the given hashing algorithm.
 * @param input the [String] to generate a hash of.
 * @param algorithm the hashing algorithm to use.
 * @return the generated hash [String].
 */
fun hashString(input: String, algorithm: String = "SHA-256"): String    {
    return MessageDigest.getInstance(algorithm)
        .digest(input.toByteArray())
        .fold("") { str, it -> str + "%02x".format(it) }
}