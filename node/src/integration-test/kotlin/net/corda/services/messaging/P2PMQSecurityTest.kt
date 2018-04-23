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
abstract class P2PMQSecurityTest : MQSecurityTest() {
    @Test
    fun `consume message from P2P queue`() {
        assertConsumeAttackFails("$P2P_PREFIX${alice.info.singleIdentity().owningKey.toStringShort()}")
    }

    @Test
    fun `consume message from peer queue`() {
        val bobParty = startBobAndCommunicateWithAlice()
        assertConsumeAttackFails("$PEERS_PREFIX${bobParty.owningKey.toStringShort()}")
    }

    @Test
    fun `send message to address of peer which has been communicated with`() {
        val bobParty = startBobAndCommunicateWithAlice()
        assertSendAttackFails("$PEERS_PREFIX${bobParty.owningKey.toStringShort()}")
    }

    @Test
    fun `create queue for peer which has not been communicated with`() {
        val bob = startNode(BOB_NAME)
        assertAllQueueCreationAttacksFail("$PEERS_PREFIX${bob.info.singleIdentity().owningKey.toStringShort()}")
    }

    @Test
    fun `create queue for unknown peer`() {
        val invalidPeerQueue = "$PEERS_PREFIX${generateKeyPair().public.toStringShort()}"
        assertAllQueueCreationAttacksFail(invalidPeerQueue)
    }

    @Test
    fun `consume message from RPC requests queue`() {
        assertConsumeAttackFailsNonexistent(RPCApi.RPC_SERVER_QUEUE_NAME)
    }

    @Test
    fun `consume message from logged in user's RPC queue`() {
        val user1Queue = loginToRPCAndGetClientQueue()
        assertConsumeAttackFailsNonexistent(user1Queue)
    }
}