/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import core.IdentityService
import core.Party
import java.security.PublicKey

/**
 * Scaffolding: a dummy identity service that just expects to have identities loaded off disk or found elsewhere.
 */
class FixedIdentityService(private val identities: List<Party>) : IdentityService {
    private val keyToParties = identities.toMapBy { it.owningKey }
    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]
}