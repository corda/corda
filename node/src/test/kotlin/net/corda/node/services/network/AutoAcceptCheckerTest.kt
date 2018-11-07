package net.corda.node.services.network

import net.corda.core.crypto.Crypto
import net.corda.core.node.JavaPackageName
import net.corda.core.node.services.AttachmentId
import net.corda.testing.common.internal.testNetworkParameters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AutoAcceptCheckerTest {

    companion object {
        val oldNetParams = testNetworkParameters()
    }

    @Test
    fun `returns true if params the same`() {
        assertTrue(autoAcceptParameters(oldNetParams, oldNetParams))
    }

    @Test
    fun `returns true for auto accept changes`() {
        val newNetParams = oldNetParams.copy(
                epoch = 12345,
                modifiedTime = Instant.now().plusMillis(1),
                whitelistedContractImplementations = mapOf("Key1" to listOf(AttachmentId.randomSHA256())),
                packageOwnership = mapOf(JavaPackageName("test") to Crypto.generateKeyPair().public))

        assertTrue(autoAcceptParameters(oldNetParams, newNetParams))
    }

    @Test
    fun `returns false if non auto accepting params are in change`() {
        val newNetParams = oldNetParams.copy(
                maxMessageSize = oldNetParams.maxMessageSize + 1
        )

        assertFalse(autoAcceptParameters(oldNetParams, newNetParams))
    }
}