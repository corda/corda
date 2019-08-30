package net.corda.node.services.keys

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.junit.Test
import kotlin.test.assertEquals

class FilterMyKeysTests {
    @Test
    fun test() {
        val name = CordaX500Name("Roger", "Office", "GB")
        val (_, services) = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = emptyList(),
                initialIdentity = TestIdentity(name),
                networkParameters = testNetworkParameters(),
                moreKeys = emptySet(),
                moreIdentities = emptySet()
        )
        val ourKey = services.keyManagementService.freshKey()
        val notOurKey = Crypto.generateKeyPair().public
        val result = services.keyManagementService.filterMyKeys(listOf(ourKey, notOurKey))
        assertEquals(listOf(ourKey), result)
    }
}