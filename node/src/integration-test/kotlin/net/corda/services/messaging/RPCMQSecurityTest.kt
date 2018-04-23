/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.services.messaging

import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.toStringShort
import net.corda.core.utilities.toBase58String
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEERS_PREFIX
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import org.junit.Test

/**
 * Runs a series of MQ-related attacks against a node. Subclasses need to call [startAttacker] to connect
 * the attacker to [alice].
 */
abstract class RPCMQSecurityTest : MQSecurityTest() {
    @Test
    fun `consume message from P2P queue`() {
        assertConsumeAttackFailsNonexistent("$P2P_PREFIX${alice.info.singleIdentity().owningKey.toStringShort()}")
    }

    @Test
    fun `consume message from peer queue`() {
        val bobParty = startBobAndCommunicateWithAlice()
        assertConsumeAttackFailsNonexistent("$PEERS_PREFIX${bobParty.owningKey.toBase58String()}")
    }

    @Test
    fun `send message to address of peer which has been communicated with`() {
        val bobParty = startBobAndCommunicateWithAlice()
        assertConsumeAttackFailsNonexistent("$PEERS_PREFIX${bobParty.owningKey.toBase58String()}")
    }

    @Test
    fun `create queue for peer which has not been communicated with`() {
        val bob = startNode(BOB_NAME)
        assertConsumeAttackFailsNonexistent("$PEERS_PREFIX${bob.info.singleIdentity().owningKey.toBase58String()}")
    }

    @Test
    fun `create queue for unknown peer`() {
        val invalidPeerQueue = "$PEERS_PREFIX${generateKeyPair().public.toBase58String()}"
        assertConsumeAttackFailsNonexistent(invalidPeerQueue)
    }

    @Test
    fun `consume message from RPC requests queue`() {
        assertConsumeAttackFails(RPCApi.RPC_SERVER_QUEUE_NAME)
    }

    @Test
    fun `consume message from logged in user's RPC queue`() {
        val user1Queue = loginToRPCAndGetClientQueue()
        assertConsumeAttackFails(user1Queue)
    }
}