/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.node.services

import org.assertj.core.api.Assertions
import org.junit.Test

class VaultEnumTypesTest {
    @Test
    fun vaultStatusReflectsOrdinalValues() {
        /**
         * Warning!!! Do not change the order of this Enum as ordinal values are stored in the database
         */
        val vaultStateStatusUnconsumed = Vault.StateStatus.UNCONSUMED
        Assertions.assertThat(vaultStateStatusUnconsumed.ordinal).isEqualTo(0)
        val vaultStateStatusConsumed = Vault.StateStatus.CONSUMED
        Assertions.assertThat(vaultStateStatusConsumed.ordinal).isEqualTo(1)
    }
}