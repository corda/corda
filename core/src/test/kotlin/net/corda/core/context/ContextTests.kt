package net.corda.core.context

import org.junit.Assert.assertFalse
import org.junit.Test

class ContextTests {

    @Test
    fun `feature flags in an unconfigured context are always false`() {
        assertFalse(FeatureFlag.DISABLE_CORDA_2707)
    }



}