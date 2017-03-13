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