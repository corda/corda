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
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import org.junit.ClassRule
import org.junit.Test

class BankOfCordaRPCClientTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(BOC_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName(),
                BIGCORP_NAME.organisation)
    }

    @Test
    fun `issuer flow via RPC`() {
        val commonPermissions = setOf(
                invokeRpc("vaultTrackByCriteria"),
                invokeRpc(CordaRPCOps::wellKnownPartyFromX500Name),
                invokeRpc(CordaRPCOps::notaryIdentities)
        )
        driver(DriverParameters(extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val bocManager = User("bocManager", "password1", permissions = setOf(
                    startFlow<CashIssueAndPaymentFlow>()) + commonPermissions)
            val bigCorpCFO = User("bigCorpCFO", "password2", permissions = emptySet<String>() + commonPermissions)
            val (nodeBankOfCorda, nodeBigCorporation) = listOf(
                    startNode(providedName = BOC_NAME, rpcUsers = listOf(bocManager)),
                    startNode(providedName = BIGCORP_NAME, rpcUsers = listOf(bigCorpCFO))
            ).map { it.getOrThrow() }

            // Bank of Corda RPC Client
            val bocClient = CordaRPCClient(nodeBankOfCorda.rpcAddress)
            val bocProxy = bocClient.start("bocManager", "password1").proxy

            // Big Corporation RPC Client
            val bigCorpClient = CordaRPCClient(nodeBigCorporation.rpcAddress)
            val bigCorpProxy = bigCorpClient.start("bigCorpCFO", "password2").proxy

            // Register for Bank of Corda Vault updates
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val vaultUpdatesBoc = bocProxy.vaultTrackByCriteria(Cash.State::class.java, criteria).updates

            // Register for Big Corporation Vault updates
            val vaultUpdatesBigCorp = bigCorpProxy.vaultTrackByCriteria(Cash.State::class.java, criteria).updates

            val bigCorporation = bigCorpProxy.wellKnownPartyFromX500Name(BIGCORP_NAME)!!

            // Kick-off actual Issuer Flow
            val anonymous = true
            bocProxy.startFlow(::CashIssueAndPaymentFlow,
                    1000.DOLLARS, OpaqueBytes.of(1),
                    bigCorporation,
                    anonymous,
                    defaultNotaryIdentity).returnValue.getOrThrow()

            // Check Bank of Corda Vault Updates
            vaultUpdatesBoc.expectEvents {
                sequence(
                        // ISSUE
                        expect { update ->
                            require(update.consumed.isEmpty()) { "Expected 0 consumed states, actual: $update" }
                            require(update.produced.size == 1) { "Expected 1 produced states, actual: $update" }
                        },
                        // MOVE
                        expect { update ->
                            require(update.consumed.size == 1) { "Expected 1 consumed states, actual: $update" }
                            require(update.produced.isEmpty()) { "Expected 0 produced states, actual: $update" }
                        }
                )
            }

            // Check Big Corporation Vault Updates
            vaultUpdatesBigCorp.expectEvents {
                sequence(
                        // MOVE
                        expect { (consumed, produced) ->
                            require(consumed.isEmpty()) { consumed.size }
                            require(produced.size == 1) { produced.size }
                        }
                )
            }
        }
    }
}