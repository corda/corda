package net.corda.node.services

import net.corda.core.contracts.withoutIssuer
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.driver
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.sequence
import org.junit.Test

class VaultServiceTest {
    class Setup(driver: DriverDSL): DriverDSL by driver {
        val aliceNode = driver.startNode()
        val bobNode = driver.startNode()
        val aliceRpc = aliceNode.getOrThrow().rpc
        val bobRpc = bobNode.getOrThrow().rpc
        val alice = aliceNode.getOrThrow().nodeInfo.legalIdentities.first()
        val bob = bobNode.getOrThrow().nodeInfo.legalIdentities.first()
        val aliceVaultUpdates = aliceRpc.vaultTrack(Cash.State::class.java).updates
    }

    fun setup(block: Setup.() -> Unit) {
        driver(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance")) {
            Setup(this).block()
        }
    }

    @Test
    fun `Cash change updates arrive correctly`() {
        setup {
            aliceRpc.startFlow(::CashIssueFlow, 2.DOLLARS, OpaqueBytes.of(0), defaultNotaryHandle.identity).returnValue.getOrThrow()
            aliceRpc.startFlow(::CashPaymentFlow, 1.DOLLARS, bob).returnValue.getOrThrow()
            bobRpc.startFlow(::CashPaymentFlow, 1.DOLLARS, alice).returnValue.getOrThrow()
            aliceVaultUpdates.expectEvents {
                sequence(
                        expect {
                            require(it.consumed.size == 0)
                            require(it.produced.size == 1)
                            require(it.produced.first().state.data.amount.withoutIssuer() == 2.DOLLARS)
                        },
                        expect {
                            require(it.consumed.size == 1)
                            require(it.consumed.first().state.data.amount.withoutIssuer() == 2.DOLLARS)
                            require(it.produced.size == 1)
                            require(it.produced.first().state.data.amount.withoutIssuer() == 1.DOLLARS)
                        },
                        expect {
                            require(it.consumed.size == 0)
                            require(it.produced.size == 1)
                            require(it.produced.first().state.data.amount.withoutIssuer() == 1.DOLLARS)
                        }
                )
            }
        }
    }

    @Test
    fun `Updates with nothing produced don't get lost`() {
        setup {
            aliceRpc.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(0), defaultNotaryHandle.identity).returnValue.getOrThrow()
            aliceRpc.startFlow(::CashPaymentFlow, 1.DOLLARS, bob).returnValue.getOrThrow()
            bobRpc.startFlow(::CashPaymentFlow, 1.DOLLARS, alice).returnValue.getOrThrow()
            aliceVaultUpdates.expectEvents {
                sequence(
                        expect {
                            require(it.consumed.size == 0)
                            require(it.produced.size == 1)
                            require(it.produced.first().state.data.amount.withoutIssuer() == 1.DOLLARS)
                        },
                        expect {
                            require(it.consumed.size == 1)
                            require(it.consumed.first().state.data.amount.withoutIssuer() == 1.DOLLARS)
                            require(it.produced.size == 0)
                        },
                        expect {
                            require(it.consumed.size == 0)
                            require(it.produced.size == 1)
                            require(it.produced.first().state.data.amount.withoutIssuer() == 1.DOLLARS)
                        }
                )
            }
        }
    }
}