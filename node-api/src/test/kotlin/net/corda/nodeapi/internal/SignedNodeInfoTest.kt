/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal

import net.corda.core.crypto.Crypto
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.signWith
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import java.security.SignatureException

class SignedNodeInfoTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val nodeInfoBuilder = TestNodeInfoBuilder()

    @Test
    fun `verifying single identity`() {
        nodeInfoBuilder.addIdentity(ALICE_NAME)
        val (nodeInfo, signedNodeInfo) = nodeInfoBuilder.buildWithSigned()
        assertThat(signedNodeInfo.verified()).isEqualTo(nodeInfo)
    }

    @Test
    fun `verifying multiple identities`() {
        nodeInfoBuilder.addIdentity(ALICE_NAME)
        nodeInfoBuilder.addIdentity(BOB_NAME)
        val (nodeInfo, signedNodeInfo) = nodeInfoBuilder.buildWithSigned()
        assertThat(signedNodeInfo.verified()).isEqualTo(nodeInfo)
    }

    @Test
    fun `verifying missing signature`() {
        val (_, aliceKey) = nodeInfoBuilder.addIdentity(ALICE_NAME)
        nodeInfoBuilder.addIdentity(BOB_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(aliceKey))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining("Missing signatures")
    }

    @Test
    fun `verifying extra signature`() {
        val (_, aliceKey) = nodeInfoBuilder.addIdentity(ALICE_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(aliceKey, generateKeyPair().private))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining("Extra signatures")
    }

    @Test
    fun `verifying incorrect signature`() {
        nodeInfoBuilder.addIdentity(ALICE_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(generateKeyPair().private))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining(ALICE_NAME.toString())
    }

    @Test
    fun `verifying with signatures in wrong order`() {
        val (_, aliceKey) = nodeInfoBuilder.addIdentity(ALICE_NAME)
        val (_, bobKey) = nodeInfoBuilder.addIdentity(BOB_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(bobKey, aliceKey))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining(ALICE_NAME.toString())
    }

    private fun generateKeyPair() = Crypto.generateKeyPair()
}
