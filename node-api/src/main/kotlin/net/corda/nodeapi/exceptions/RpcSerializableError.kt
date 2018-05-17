package net.corda.nodeapi.exceptions

import net.corda.core.serialization.CordaSerializable

/**
 * Allows an implementing [Throwable] to be propagated to RPC clients.
 */
@Deprecated(message = "Replace with ClientRelevantError.", replaceWith = ReplaceWith("net.corda.core.flows.ClientRelevantError", "import net.corda.core.flows.ClientRelevantError"))
@CordaSerializable
interface RpcSerializableError