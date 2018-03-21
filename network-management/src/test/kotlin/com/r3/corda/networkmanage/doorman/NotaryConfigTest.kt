/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman

import com.google.common.jimfs.Jimfs
import net.corda.core.internal.copyTo
import net.corda.core.serialization.serialize
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createNodeInfoAndSigned
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.*

class NotaryConfigTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val fs = Jimfs.newFileSystem()

    @After
    fun cleanUp() {
        fs.close()
    }

    @Test
    fun toNotaryInfo() {
        val (nodeInfo, signedNodeInfo) = createNodeInfoAndSigned(ALICE_NAME)

        val file = fs.getPath(UUID.randomUUID().toString())
        signedNodeInfo.serialize().open().copyTo(file)

        val notaryInfo = NotaryConfig(file, true).toNotaryInfo()
        assertThat(notaryInfo.identity).isEqualTo(nodeInfo.legalIdentities[0])
        assertThat(notaryInfo.validating).isTrue()
    }
}
