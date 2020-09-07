package net.corda.core.internal.context

import net.corda.core.serialization.CordaSerializable
import java.time.Duration

@CordaSerializable
data class DurableStreamContext(val currentPosition: Long, val maxCount: Int, val awaitForResultTimeout: Duration)