package net.corda.core.context

import net.corda.core.serialization.CordaSerializable

/**
 * Authentication / Authorisation Service ID.
 */
@CordaSerializable
data class AuthServiceId(val value: String)