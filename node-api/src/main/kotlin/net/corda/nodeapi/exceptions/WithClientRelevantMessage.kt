package net.corda.nodeapi.exceptions

import net.corda.core.serialization.CordaSerializable

/**
 * Mark an [Exception] to be shown to RPC clients.
 */
@CordaSerializable
interface WithClientRelevantMessage