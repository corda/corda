package net.corda.nodeapi.exceptions

import net.corda.core.serialization.CordaSerializable

/**
 * Allows an implementing [Throwable] to be propagated to RPC clients.
 */
@CordaSerializable
interface RpcSerializableError