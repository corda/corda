package net.corda.coretests.contracts

import net.corda.core.contracts.CordaRotatedKeys
import net.corda.core.contracts.RotatedKeys
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.sha256
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.hash
import net.corda.core.internal.retrieveRotatedKeys
import net.corda.core.node.ServiceHub
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.SelfCleaningDir
import net.corda.testing.node.MockServices
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RotatedKeysTest {

    @Test(timeout = 300_000)
    fun `validateDefaultRotatedKeysAreRetrievableFromMockServices`() {
        val services: ServiceHub = MockServices(TestIdentity(CordaX500Name("MegaCorp", "London", "GB")))
        val rotatedKeys = services.retrieveRotatedKeys()
        assertEquals( CordaRotatedKeys.keys.rotatedSigningKeys, rotatedKeys.rotatedSigningKeys)
    }

    @Test(timeout = 300_000)
    fun `when input and output keys are the same canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKey = file.path.generateKey()
            val rotatedKeys = RotatedKeys()
            assertTrue(rotatedKeys.canBeTransitioned(publicKey, publicKey))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output keys are the same and output is a list canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKey = file.path.generateKey()
            val rotatedKeys = RotatedKeys()
            assertTrue(rotatedKeys.canBeTransitioned(publicKey, listOf(publicKey)))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output keys are different and output is a list canBeTransitioned returns false`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey("AAAA")
            val publicKeyB = file.path.generateKey("BBBB")
            val rotatedKeys = RotatedKeys()
            assertFalse(rotatedKeys.canBeTransitioned(publicKeyA, listOf(publicKeyB)))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output keys are different and rotated and output is a list canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey("AAAA")
            val publicKeyB = file.path.generateKey("BBBB")
            val rotatedKeys = RotatedKeys(listOf((listOf(publicKeyA.hash.sha256(), publicKeyB.hash.sha256()))))
            assertTrue(rotatedKeys.canBeTransitioned(publicKeyA, listOf(publicKeyB)))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output keys are the same and both are lists canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKey = file.path.generateKey()
            val rotatedKeys = RotatedKeys()
            assertTrue(rotatedKeys.canBeTransitioned(listOf(publicKey), listOf(publicKey)))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output keys are different and rotated and both are lists canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val rotatedKeys = RotatedKeys(listOf((listOf(publicKeyA.hash.sha256(), publicKeyB.hash.sha256()))))
            assertTrue(rotatedKeys.canBeTransitioned(listOf(publicKeyA), listOf(publicKeyB)))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output keys are different canBeTransitioned returns false`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val rotatedKeys = RotatedKeys()
            assertFalse(rotatedKeys.canBeTransitioned(publicKeyA, publicKeyB))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output keys are different but are rotated canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val rotatedKeysData = listOf((listOf(publicKeyA.hash.sha256(), publicKeyB.hash.sha256())))
            val rotatedKeys = RotatedKeys(rotatedKeysData)
            assertTrue(rotatedKeys.canBeTransitioned(publicKeyA, publicKeyB))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output keys are different with multiple rotations canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            val publicKeyD = file.path.generateKey(alias = "DDDD")
            val rotatedKeysData = listOf(listOf(publicKeyA.hash.sha256(), publicKeyB.hash.sha256()),
                                         listOf(publicKeyC.hash.sha256(), publicKeyD.hash.sha256()))
            val rotatedKeys = RotatedKeys(rotatedKeysData)
            assertTrue(rotatedKeys.canBeTransitioned(publicKeyA, publicKeyB))
        }
    }

    @Test(timeout = 300_000)
    fun `when multiple input and output keys are different with multiple rotations canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            val publicKeyD = file.path.generateKey(alias = "DDDD")
            val rotatedKeysData = listOf(listOf(publicKeyA.hash.sha256(), publicKeyC.hash.sha256()),
                    listOf(publicKeyB.hash.sha256(), publicKeyD.hash.sha256()))
            val rotatedKeys = RotatedKeys(rotatedKeysData)
            val compositeKeyInput = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val compositeKeyOutput = CompositeKey.Builder().addKeys(publicKeyC, publicKeyD).build()
            assertTrue(rotatedKeys.canBeTransitioned(compositeKeyInput, compositeKeyOutput))
        }
    }

    @Test(timeout = 300_000)
    fun `when multiple input and output keys are diff and diff ordering with multiple rotations canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            val publicKeyD = file.path.generateKey(alias = "DDDD")
            val rotatedKeysData = listOf(listOf(publicKeyA.hash.sha256(), publicKeyC.hash.sha256()),
                    listOf(publicKeyB.hash.sha256(), publicKeyD.hash.sha256()))
            val rotatedKeys = RotatedKeys(rotatedKeysData)

            val compositeKeyInput = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val compositeKeyOutput = CompositeKey.Builder().addKeys(publicKeyD, publicKeyC).build()
            assertTrue(rotatedKeys.canBeTransitioned(compositeKeyInput, compositeKeyOutput))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output key are composite and the same canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val compositeKey = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val rotatedKeys = RotatedKeys()
            assertTrue(rotatedKeys.canBeTransitioned(compositeKey, compositeKey))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output key are composite and different canBeTransitioned returns false`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            val compositeKeyInput = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val compositeKeyOutput = CompositeKey.Builder().addKeys(publicKeyA, publicKeyC).build()
            val rotatedKeys = RotatedKeys()
            assertFalse(rotatedKeys.canBeTransitioned(compositeKeyInput, compositeKeyOutput))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output key are composite and different but key is rotated canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            val compositeKeyInput = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val compositeKeyOutput = CompositeKey.Builder().addKeys(publicKeyA, publicKeyC).build()
            val rotatedKeys = RotatedKeys(listOf((listOf(publicKeyB.hash.sha256(), publicKeyC.hash.sha256()))))
            assertTrue(rotatedKeys.canBeTransitioned(compositeKeyInput, compositeKeyOutput))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output key are composite and different and diff key is rotated canBeTransitioned returns false`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            val compositeKeyInput = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val compositeKeyOutput = CompositeKey.Builder().addKeys(publicKeyA, publicKeyC).build()
            val rotatedKeys = RotatedKeys(listOf((listOf(publicKeyA.hash.sha256(), publicKeyC.hash.sha256()))))
            assertFalse(rotatedKeys.canBeTransitioned(compositeKeyInput, compositeKeyOutput))
        }
    }

    @Test(timeout = 300_000)
    fun `when input is composite (1 key) and output is composite (2 keys) canBeTransitioned returns false`() {
        // For composite keys number of input and output leaves must be the same, in canBeTransitioned check.
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val compositeKeyInput = CompositeKey.Builder().addKeys(publicKeyA).build()
            val compositeKeyOutput = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val rotatedKeys = RotatedKeys()
            assertFalse(rotatedKeys.canBeTransitioned(compositeKeyInput, compositeKeyOutput))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output key are composite with 2 levels and the same canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            val publicKeyD = file.path.generateKey(alias = "DDDD")
            val compositeKeyA = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val compositeKeyB = CompositeKey.Builder().addKeys(publicKeyC, publicKeyD).build()
            val compositeKeyC = CompositeKey.Builder().addKeys(compositeKeyA, compositeKeyB).build()
            val rotatedKeys = RotatedKeys()
            assertTrue(rotatedKeys.canBeTransitioned(compositeKeyC, compositeKeyC))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output key are different & composite & rotated with 2 levels canBeTransitioned returns true`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            val publicKeyD = file.path.generateKey(alias = "DDDD")

            // in output DDDD has rotated to EEEE
            val publicKeyE = file.path.generateKey(alias = "EEEE")
            val compositeKeyA = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val compositeKeyB = CompositeKey.Builder().addKeys(publicKeyC, publicKeyD).build()
            val compositeKeyC = CompositeKey.Builder().addKeys(publicKeyC, publicKeyE).build()

            val compositeKeyInput = CompositeKey.Builder().addKeys(compositeKeyA, compositeKeyB).build()
            val compositeKeyOutput = CompositeKey.Builder().addKeys(compositeKeyA, compositeKeyC).build()

            val rotatedKeys = RotatedKeys(listOf((listOf(publicKeyD.hash.sha256(), publicKeyE.hash.sha256()))))
            assertTrue(rotatedKeys.canBeTransitioned(compositeKeyInput, compositeKeyOutput))
        }
    }

    @Test(timeout = 300_000)
    fun `when input and output key are different & composite & not rotated with 2 levels canBeTransitioned returns false`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            val publicKeyD = file.path.generateKey(alias = "DDDD")

            // in output DDDD has rotated to EEEE
            val publicKeyE = file.path.generateKey(alias = "EEEE")
            val compositeKeyA = CompositeKey.Builder().addKeys(publicKeyA, publicKeyB).build()
            val compositeKeyB = CompositeKey.Builder().addKeys(publicKeyC, publicKeyD).build()
            val compositeKeyC = CompositeKey.Builder().addKeys(publicKeyC, publicKeyE).build()

            val compositeKeyInput = CompositeKey.Builder().addKeys(compositeKeyA, compositeKeyB).build()
            val compositeKeyOutput = CompositeKey.Builder().addKeys(compositeKeyA, compositeKeyC).build()

            val rotatedKeys = RotatedKeys()
            assertFalse(rotatedKeys.canBeTransitioned(compositeKeyInput, compositeKeyOutput))
        }
    }

    @Test(timeout = 300_000, expected = IllegalStateException::class)
    fun `when key is repeated in rotated list, throws exception`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            RotatedKeys(listOf(listOf(publicKeyA.hash.sha256(), publicKeyB.hash.sha256(), publicKeyA.hash.sha256())))
        }
    }

    @Test(timeout = 300_000, expected = IllegalStateException::class)
    fun `when key is repeated across rotated lists, throws exception`() {
        SelfCleaningDir().use { file ->
            val publicKeyA = file.path.generateKey(alias = "AAAA")
            val publicKeyB = file.path.generateKey(alias = "BBBB")
            val publicKeyC = file.path.generateKey(alias = "CCCC")
            RotatedKeys(listOf(listOf(publicKeyA.hash.sha256(), publicKeyB.hash.sha256()), listOf(publicKeyC.hash.sha256(), publicKeyA.hash.sha256())))
        }
    }
}