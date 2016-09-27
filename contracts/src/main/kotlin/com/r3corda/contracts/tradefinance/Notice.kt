package com.r3corda.contracts.tradefinance

import java.security.PublicKey
import java.util.*

/**
 * A notice which can be attached to a receivable.
 */
sealed class Notice(val id: UUID, val owner: PublicKey) {
    class OwnershipInterest(id: UUID, owner: PublicKey) : Notice(id, owner)
    class Objection(id: UUID, owner: PublicKey) : Notice(id, owner)
}