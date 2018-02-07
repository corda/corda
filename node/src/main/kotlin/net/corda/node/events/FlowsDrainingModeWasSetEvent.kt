package net.corda.node.events

import java.time.Instant
import java.time.Instant.now

// we'll need to turn this into an interface if we ever decide to expose this to customer's code
// we might want to introduce a super-type hierarchy
data class FlowsDrainingModeSetEvent internal constructor(val value: Boolean, override val timestamp: Instant = now()) : Event