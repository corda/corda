package net.corda.core

import net.corda.core.serialization.CordaSerializable

/**
 * Allows an implementing [Throwable] to be propagated to clients.
 */
@CordaSerializable
interface ClientRelevantError