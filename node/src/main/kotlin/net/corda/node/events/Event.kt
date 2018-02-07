package net.corda.node.events

import java.time.Instant

// this might get moved to core as part of the work on business events
interface Event {

    val timestamp: Instant
}