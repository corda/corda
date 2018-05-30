package net.corda.core.serialization

import net.corda.core.CordaException
import net.corda.core.Deterministic
import net.corda.core.crypto.SecureHash

/** Thrown during deserialization to indicate that an attachment needed to construct the [WireTransaction] is not found. */
@Deterministic
@CordaSerializable
class MissingAttachmentsException(val ids: List<SecureHash>) : CordaException()