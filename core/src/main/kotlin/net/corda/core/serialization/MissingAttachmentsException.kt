package net.corda.core.serialization

import net.corda.core.CordaException
import net.corda.core.crypto.SecureHash

/** Thrown during deserialisation to indicate that an attachment needed to construct the [WireTransaction] is not found. */
@CordaSerializable
class MissingAttachmentsException(val ids: List<SecureHash>) : CordaException()