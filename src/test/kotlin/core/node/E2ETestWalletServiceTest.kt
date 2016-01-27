/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import contracts.Cash
import core.DOLLARS
import core.MockKeyManagementService
import core.MockServices
import core.ServiceHub
import core.testutils.ALICE
import core.testutils.ALICE_KEY
import org.junit.Test
import java.security.KeyPair
import java.util.*
import kotlin.test.assertEquals

class E2ETestWalletServiceTest {
    val services: ServiceHub = MockServices(
            keyManagement = MockKeyManagementService(emptyMap(), arrayListOf<KeyPair>(ALICE_KEY, ALICE_KEY, ALICE_KEY))
    )

    @Test fun splits() {
        val wallet = E2ETestWalletService(services)
        // Fix the PRNG so that we get the same splits every time.
        wallet.fillWithSomeTestCash(100.DOLLARS, 3, 3, Random(0L))

        val w = wallet.currentWallet
        assertEquals(3, w.states.size)

        val state = w.states[0].state as Cash.State
        assertEquals(services.storageService.myLegalIdentity, state.deposit.party)
        assertEquals(services.storageService.myLegalIdentityKey.public, state.deposit.party.owningKey)
        assertEquals(29.01.DOLLARS, state.amount)
        assertEquals(ALICE, state.owner)

        assertEquals(33.34.DOLLARS, (w.states[2].state as Cash.State).amount)
        assertEquals(35.61.DOLLARS, (w.states[1].state as Cash.State).amount)
    }
}
