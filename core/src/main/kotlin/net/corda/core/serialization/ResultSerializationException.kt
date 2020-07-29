package net.corda.core.serialization

import net.corda.core.CordaRuntimeException
import net.corda.core.serialization.internal.MissingSerializerException

class ResultSerializationException(e: MissingSerializerException) : CordaRuntimeException(e.message)