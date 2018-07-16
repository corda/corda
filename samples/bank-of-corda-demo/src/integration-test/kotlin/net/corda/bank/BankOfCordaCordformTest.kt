/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bank

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.withoutIssuer
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.utils.sumCash
import net.corda.testing.core.BOC_NAME
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.internal.demorun.nodeRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

class BankOfCordaCordformTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas("NotaryService", "BankOfCorda", BIGCORP_NAME.organisation)
    }

    @Test
    fun `run demo`() {
        BankOfCordaCordform().nodeRunner().scanPackages(listOf("net.corda.finance")).deployAndRunNodesAndThen {
            IssueCash.requestRpcIssue(20000.DOLLARS)
            CordaRPCClient(NetworkHostAndPort("localhost", BOC_RPC_PORT)).use(BOC_RPC_USER, BOC_RPC_PWD) {
                assertThat(it.proxy.vaultQuery(Cash.State::class.java).states).isEmpty()  // All of the issued cash is transferred
            }
            CordaRPCClient(NetworkHostAndPort("localhost", BIGCORP_RPC_PORT)).use(BIGCORP_RPC_USER, BIGCORP_RPC_PWD) {
                val cashStates = it.proxy.vaultQuery(Cash.State::class.java).states.map { it.state.data }
                val knownOwner = it.proxy.wellKnownPartyFromAnonymous(cashStates.map { it.owner }.toSet().single())
                assertThat(knownOwner?.name).isEqualTo(BIGCORP_NAME)
                val totalCash = cashStates.sumCash()
                assertThat(totalCash.token.issuer.party.nameOrNull()).isEqualTo(BOC_NAME)
                assertThat(totalCash.withoutIssuer()).isEqualTo(20000.DOLLARS)
            }
        }
    }
}
