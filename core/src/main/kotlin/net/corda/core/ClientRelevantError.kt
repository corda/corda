package net.corda.core

import net.corda.core.serialization.CordaSerializable

/**
 * Allows an implementing [Throwable] to be propagated to clients.
 */
@CordaSerializable
@KeepForDJVM
@Deprecated("This is no longer used as the exception obfuscation feature is no longer available.")
interface ClientRelevantError