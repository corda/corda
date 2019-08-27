package net.corda.contracts

import net.corda.core.contracts.HashAttachmentConstraint
import net.corda.core.contracts.SignatureAttachmentConstraint
import org.junit.Assume.assumeFalse
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

open class SignatureConstraintMigrationFromHashConstraintsTests : SignatureConstraintVersioningTests() {

    @Test
    fun `auto migration from HashConstraint to SignatureConstraint`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
                startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is SignatureAttachmentConstraint)
    }

    @Test
    fun `HashConstraint cannot be migrated if 'disableHashConstraints' system property is not set to true`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = emptyMap(),
                startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is HashAttachmentConstraint)
    }

    @Test
    fun `HashConstraint cannot be migrated to SignatureConstraint if new jar is not signed`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newUnsignedCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
                startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is HashAttachmentConstraint)
    }

    @Test
    fun `HashConstraint cannot be migrated to SignatureConstraint if platform version is not 4 or greater`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
                startNodesInProcess = false,
                minimumPlatformVersion = 3
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is HashAttachmentConstraint)
    }

    @Test
    fun `HashConstraint cannot be migrated to SignatureConstraint if a HashConstraint is specified for one state and another uses an AutomaticPlaceholderConstraint`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
                startNodesInProcess = false,
                specifyExistingConstraint = true,
                addAnotherAutomaticConstraintState = true
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(2, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs[0].constraint is HashAttachmentConstraint)
        assertTrue(consumingTransaction.outputs[1].constraint is HashAttachmentConstraint)
        assertEquals(
                issuanceTransaction.outputs.single().constraint,
                consumingTransaction.outputs.first().constraint,
                "The constraint from the issuance transaction should be the same constraint used in the consuming transaction"
        )
        assertEquals(
                consumingTransaction.outputs[0].constraint,
                consumingTransaction.outputs[1].constraint,
                "The AutomaticPlaceholderConstraint of the second state should become the same HashConstraint used in other state"
        )
    }
}