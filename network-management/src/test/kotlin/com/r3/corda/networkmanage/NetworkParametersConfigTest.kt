/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage

import com.google.common.jimfs.Jimfs
import com.r3.corda.networkmanage.doorman.NetworkParametersConfig
import com.r3.corda.networkmanage.doorman.NotaryConfig
import net.corda.core.internal.copyTo
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createNodeInfoAndSigned
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.time.Instant
import java.util.*

class NetworkParametersConfigTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val fs = Jimfs.newFileSystem()

    @After
    fun cleanUp() {
        fs.close()
    }

    @Test
    fun toNetworkParameters() {
        val (aliceNodeInfo, aliceSignedNodeInfo) = createNodeInfoAndSigned(ALICE_NAME)
        val (bobNodeInfo, bobSignedNodeInfo) = createNodeInfoAndSigned(BOB_NAME)
        val networkParametersConfig = NetworkParametersConfig(
                notaries = listOf(
                        NotaryConfig(aliceSignedNodeInfo.writeToFile(), true),
                        NotaryConfig(bobSignedNodeInfo.writeToFile(), false)
                ),
                maxMessageSize = 100,
                maxTransactionSize = 100,
                minimumPlatformVersion = 3
        )

        val modifiedTime = Instant.now()
        val networkParameters = networkParametersConfig.toNetworkParameters(modifiedTime = modifiedTime, epoch = 2)
        assertThat(networkParameters.modifiedTime).isEqualTo(modifiedTime)
        assertThat(networkParameters.epoch).isEqualTo(2)
        assertThat(networkParameters.notaries).containsExactly(
                NotaryInfo(aliceNodeInfo.legalIdentities[0], true),
                NotaryInfo(bobNodeInfo.legalIdentities[0], false)
        )
        assertThat(networkParameters.maxMessageSize).isEqualTo(100)
        assertThat(networkParameters.maxTransactionSize).isEqualTo(100)
        assertThat(networkParameters.minimumPlatformVersion).isEqualTo(3)
    }

    private fun SignedNodeInfo.writeToFile(): Path {
        val path = fs.getPath(UUID.randomUUID().toString())
        serialize().open().copyTo(path)
        return path
    }
}
