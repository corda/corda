/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node.internal

import net.corda.core.serialization.internal.effectiveSerializationEnv
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class InternalMockNetworkTests {
    @Test
    fun `does not leak serialization env if init fails`() {
        val e = Exception("didn't work")
        assertThatThrownBy {
            object : InternalMockNetwork(cordappsForAllNodes = emptySet()) {
                override fun createNotaries() = throw e
            }
        }.isSameAs(e)
        assertThatThrownBy { effectiveSerializationEnv }.isInstanceOf(IllegalStateException::class.java)
    }
}