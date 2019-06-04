package net.corda.node

import net.corda.core.contracts.HashAttachmentConstraint
import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.withoutIssuer
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.internal.NetworkParametersReader
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.SelfCleaningDir
import net.corda.testing.driver.*
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test

class CordappConstraintsTests {

    companion object {
        val user = User("u", "p", setOf(startFlow<CashIssueFlow>(), startFlow<CashPaymentFlow>(),
                invokeRpc(CordaRPCOps::wellKnownPartyFromX500Name),
                invokeRpc(CordaRPCOps::notaryIdentities),
                invokeRpc("vaultTrackByCriteria")))
        val UNSIGNED_FINANCE_CORDAPP = cordappWithPackages("net.corda.finance", "migration", "META-INF.services")
        val SIGNED_FINANCE_CORDAPP = UNSIGNED_FINANCE_CORDAPP.signed()
    }

    @Test
    fun `issue cash using signature constraints`() {
        driver(DriverParameters(
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                cordappsForAllNodes = emptyList(),
                inMemoryDB = false
        )) {
            val alice = startNode(NodeParameters(
                    additionalCordapps = listOf(SIGNED_FINANCE_CORDAPP),
                    providedName = ALICE_NAME,
                    rpcUsers = listOf(user)
            )).getOrThrow()

            val expected = 500.DOLLARS
            val ref = OpaqueBytes.of(0x01)
            val issueTx = alice.rpc.startFlow(::CashIssueFlow, expected, ref, defaultNotaryIdentity).returnValue.getOrThrow()
            println("Issued transaction: $issueTx")

            // Query vault
            val states = alice.rpc.vaultQueryBy<Cash.State>().states
            printVault(alice, states)

            assertThat(states).hasSize(1)
            assertThat(states[0].state.constraint).isInstanceOf(SignatureAttachmentConstraint::class.java)
        }
    }

    @Test
    fun `issue cash using hash and signature constraints`() {
        driver(DriverParameters(
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                cordappsForAllNodes = emptyList(),
                inMemoryDB = false
        )) {
            println("Starting the node using unsigned contract jar ...")
            val alice = startNode(NodeParameters(
                    providedName = ALICE_NAME,
                    additionalCordapps = listOf(UNSIGNED_FINANCE_CORDAPP),
                    rpcUsers = listOf(user)
            )).getOrThrow()

            val expected = 500.DOLLARS
            val ref = OpaqueBytes.of(0x01)
            val issueTx = alice.rpc.startFlow(::CashIssueFlow, expected, ref, defaultNotaryIdentity).returnValue.getOrThrow()
            println("Issued transaction: $issueTx")

            // Query vault
            val states = alice.rpc.vaultQueryBy<Cash.State>().states
            printVault(alice, states)
            assertThat(states).hasSize(1)

            // Restart the node and re-query the vault
            println("Shutting down the node ...")
            (alice as OutOfProcess).process.destroyForcibly()
            alice.stop()

            println("Restarting the node using signed contract jar ...")
            (baseDirectory(ALICE_NAME) / "cordapps").deleteRecursively()
            val restartedNode = startNode(NodeParameters(
                    providedName = ALICE_NAME,
                    additionalCordapps = listOf(SIGNED_FINANCE_CORDAPP)
            )).getOrThrow()

            val statesAfterRestart = restartedNode.rpc.vaultQueryBy<Cash.State>().states
            printVault(restartedNode, statesAfterRestart)
            assertThat(statesAfterRestart).hasSize(1)

            val issueTx2 = restartedNode.rpc.startFlow(::CashIssueFlow, expected, ref, defaultNotaryIdentity).returnValue.getOrThrow()
            println("Issued 2nd transaction: $issueTx2")

            // Query vault
            val allStates = restartedNode.rpc.vaultQueryBy<Cash.State>().states
            printVault(restartedNode, allStates)

            assertThat(allStates).hasSize(2)
            // cannot guarantee database results ordering so check for one or the other.
            assertThat(allStates[0].state.constraint).isInstanceOfAny(HashAttachmentConstraint::class.java, SignatureAttachmentConstraint::class.java)
            if (allStates[0].state.constraint is HashAttachmentConstraint)
                assertThat(allStates[1].state.constraint).isInstanceOf(SignatureAttachmentConstraint::class.java)
            else
                assertThat(allStates[1].state.constraint).isInstanceOf(HashAttachmentConstraint::class.java)
        }
    }

    @Test
    fun `issue and consume cash using hash constraints`() {
        driver(DriverParameters(
                cordappsForAllNodes = listOf(UNSIGNED_FINANCE_CORDAPP),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                inMemoryDB = false
        )) {
            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user))
            ).map { it.getOrThrow() }

            // Issue Cash
            val issueTx = alice.rpc.startFlow(::CashIssueFlow, 1000.DOLLARS,  OpaqueBytes.of(1), defaultNotaryIdentity).returnValue.getOrThrow()
            println("Issued transaction: $issueTx")

            // Query vault
            val states = alice.rpc.vaultQueryBy<Cash.State>().states
            printVault(alice, states)

            // Register for Bob vault updates
            val vaultUpdatesBob = bob.rpc.vaultTrackByCriteria(Cash.State::class.java, QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)).updates

            // Transfer Cash
            val bobParty = bob.rpc.wellKnownPartyFromX500Name(BOB_NAME)!!
            val transferTx = alice.rpc.startFlow(::CashPaymentFlow, 1000.DOLLARS, bobParty, true, defaultNotaryIdentity).returnValue.getOrThrow()
            println("Payment transaction: $transferTx")

            // Query vaults
            val aliceQuery = alice.rpc.vaultQueryBy<Cash.State>(QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.CONSUMED))
            val aliceStates = aliceQuery.states
            printVault(alice, aliceQuery.states)

            assertThat(aliceStates).hasSize(1)
            assertThat(aliceStates[0].state.data.amount.withoutIssuer()).isEqualTo(1000.DOLLARS)
            assertThat(aliceQuery.statesMetadata[0].status).isEqualTo(Vault.StateStatus.CONSUMED)
            assertThat(aliceQuery.statesMetadata[0].constraintInfo!!.type()).isEqualTo(Vault.ConstraintInfo.Type.HASH)

            // Check Bob Vault Updates
            vaultUpdatesBob.expectEvents {
                sequence(
                        // MOVE
                        expect { (consumed, produced) ->
                            require(consumed.isEmpty()) { consumed.size }
                            require(produced.size == 1) { produced.size }
                        }
                )
            }

            val bobQuery = bob.rpc.vaultQueryBy<Cash.State>()
            val bobStates = bobQuery.states
            printVault(bob, bobQuery.states)

            assertThat(bobStates).hasSize(1)
            assertThat(bobStates[0].state.data.amount.withoutIssuer()).isEqualTo(1000.DOLLARS)
            assertThat(bobQuery.statesMetadata[0].status).isEqualTo(Vault.StateStatus.UNCONSUMED)
            assertThat(bobQuery.statesMetadata[0].constraintInfo!!.type()).isEqualTo(Vault.ConstraintInfo.Type.HASH)
        }
    }

    @Test
    fun `issue and consume cash using signature constraints`() {
        driver(DriverParameters(
                cordappsForAllNodes = listOf(SIGNED_FINANCE_CORDAPP),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                inMemoryDB = false
        )) {
            val (alice, bob) = listOf(
                    startNode(NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(user))),
                    startNode(NodeParameters(providedName = BOB_NAME, rpcUsers = listOf(user)))
            ).map { it.getOrThrow() }

            // Issue Cash
            val issueTx = alice.rpc.startFlow(::CashIssueFlow, 1000.DOLLARS,  OpaqueBytes.of(1), defaultNotaryIdentity).returnValue.getOrThrow()
            println("Issued transaction: $issueTx")

            // Query vault
            val states = alice.rpc.vaultQueryBy<Cash.State>().states
            printVault(alice, states)

            // Register for Bob vault updates
            val vaultUpdatesBob = bob.rpc.vaultTrackByCriteria(Cash.State::class.java, QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)).updates

            // Transfer Cash
            val bobParty = bob.rpc.wellKnownPartyFromX500Name(BOB_NAME)!!
            val transferTx = alice.rpc.startFlow(::CashPaymentFlow, 1000.DOLLARS, bobParty).returnValue.getOrThrow()
            println("Payment transaction: $transferTx")

            // Query vaults
            val aliceQuery = alice.rpc.vaultQueryBy<Cash.State>(QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.CONSUMED))
            val aliceStates = aliceQuery.states
            printVault(alice, aliceStates)

            assertThat(aliceStates).hasSize(1)
            assertThat(aliceStates[0].state.data.amount.withoutIssuer()).isEqualTo(1000.DOLLARS)
            assertThat(aliceQuery.statesMetadata[0].status).isEqualTo(Vault.StateStatus.CONSUMED)
            assertThat(aliceQuery.statesMetadata[0].constraintInfo!!.type()).isEqualTo(Vault.ConstraintInfo.Type.SIGNATURE)

            // Check Bob Vault Updates
            vaultUpdatesBob.expectEvents {
                sequence(
                        // MOVE
                        expect { (consumed, produced) ->
                            require(consumed.isEmpty()) { consumed.size }
                            require(produced.size == 1) { produced.size }
                        }
                )
            }

            val bobQuery = bob.rpc.vaultQueryBy<Cash.State>()
            val bobStates = bobQuery.states
            printVault(bob, bobStates)

            assertThat(bobStates).hasSize(1)
            assertThat(bobStates[0].state.data.amount.withoutIssuer()).isEqualTo(1000.DOLLARS)
            assertThat(bobQuery.statesMetadata[0].status).isEqualTo(Vault.StateStatus.UNCONSUMED)
            assertThat(bobQuery.statesMetadata[0].constraintInfo!!.type()).isEqualTo(Vault.ConstraintInfo.Type.SIGNATURE)
        }
    }

    @Test
    @Ignore    // TODO(mike): rework
    fun `issue cash and transfer using hash to signature constraints migration`() {
        // signing key setup
        val keyStoreDir = SelfCleaningDir()
        val packageOwnerKey = keyStoreDir.path.generateKey()

        driver(DriverParameters(
                cordappsForAllNodes = listOf(UNSIGNED_FINANCE_CORDAPP),
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = false)),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                inMemoryDB = false
        )) {
            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user))
            ).map { it.getOrThrow() }

            val notary = defaultNotaryHandle.nodeHandles.get().first()

            // Issue Cash
            val issueTx = alice.rpc.startFlow(::CashIssueFlow, 1000.DOLLARS, OpaqueBytes.of(1), defaultNotaryIdentity)
                    .returnValue.getOrThrow()
            println("Issued transaction: $issueTx")

            // Query vault
            val states = alice.rpc.vaultQueryBy<Cash.State>().states
            printVault(alice, states)

            // Claim the package, publish the new network parameters , and restart all nodes.
            val parameters = NetworkParametersReader(DEV_ROOT_CA.certificate, null, notary.baseDirectory).read().networkParameters

            val newParams = parameters.copy(
                    packageOwnership = mapOf("net.corda.finance.contracts.asset" to packageOwnerKey)
            )
            listOf(alice, bob, notary).forEach { node ->
                println("Shutting down the node for ${node} ... ")
                (node as OutOfProcess).process.destroyForcibly()
                node.stop()
                NetworkParametersCopier(newParams, overwriteFile = true).install(node.baseDirectory)
            }

            startNode(providedName = defaultNotaryIdentity.name)

            println("Restarting the node for $ALICE_NAME ...")
            (baseDirectory(ALICE_NAME) / "cordapps").deleteRecursively()
            val restartedAlice = startNode(NodeParameters(
                    providedName = ALICE_NAME,
                    additionalCordapps = listOf(UNSIGNED_FINANCE_CORDAPP.signed(keyStoreDir.path))
            )).getOrThrow()

            println("Restarting the node for $BOB_NAME ...")
            (baseDirectory(BOB_NAME) / "cordapps").deleteRecursively()
            val restartedBob = startNode(NodeParameters(
                    providedName = BOB_NAME,
                    additionalCordapps = listOf(UNSIGNED_FINANCE_CORDAPP.signed(keyStoreDir.path))
            )).getOrThrow()

            // Register for Bob vault updates
            val vaultUpdatesBob = restartedBob.rpc.vaultTrackByCriteria(Cash.State::class.java, QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)).updates

            // Transfer Cash
            val bobParty = restartedBob.rpc.wellKnownPartyFromX500Name(BOB_NAME)!!
            val transferTxn = restartedAlice.rpc.startFlow(::CashPaymentFlow, 1000.DOLLARS, bobParty, true, defaultNotaryIdentity).returnValue.getOrThrow()
            println("Payment transaction: $transferTxn")

            // Query vault
            println("Vault query for ALICE after cash transfer ...")
            val aliceQuery = restartedAlice.rpc.vaultQueryBy<Cash.State>(QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.CONSUMED))
            val aliceStates = aliceQuery.states
            printVault(alice, aliceStates)

            assertThat(aliceStates).hasSize(1)
            assertThat(aliceStates[0].state.data.amount.withoutIssuer()).isEqualTo(1000.DOLLARS)
            assertThat(aliceQuery.statesMetadata[0].status).isEqualTo(Vault.StateStatus.CONSUMED)
            assertThat(aliceQuery.statesMetadata[0].constraintInfo!!.type()).isEqualTo(Vault.ConstraintInfo.Type.HASH)

            // Check Bob Vault Updates
            vaultUpdatesBob.expectEvents {
                sequence(
                        // MOVE
                        expect { (consumed, produced) ->
                            require(consumed.isEmpty()) { consumed.size }
                            require(produced.size == 1) { produced.size }
                        }
                )
            }

            println("Vault query for BOB after cash transfer ...")
            val bobQuery = restartedBob.rpc.vaultQueryBy<Cash.State>()
            val bobStates = bobQuery.states
            printVault(bob, bobStates)

            assertThat(bobStates).hasSize(1)
            assertThat(bobStates[0].state.data.amount.withoutIssuer()).isEqualTo(1000.DOLLARS)
            assertThat(bobQuery.statesMetadata[0].status).isEqualTo(Vault.StateStatus.UNCONSUMED)
            assertThat(bobQuery.statesMetadata[0].constraintInfo!!.type()).isEqualTo(Vault.ConstraintInfo.Type.SIGNATURE)

            // clean-up
            keyStoreDir.close()
        }
    }

    private fun printVault(node: NodeHandle, states: List<StateAndRef<Cash.State>>) {
        println("Vault query for ${node.nodeInfo.singleIdentity()} returned ${states.size} records")
        states.forEach {
            println(it.state)
        }
    }
}
