package com.r3corda.core

import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilsTest {
    fun `ordered and unique basic`() {
        val basic = listOf(1, 2, 3, 5, 8)
        assertTrue(basic.isOrderedAndUnique { this })

        val negative = listOf(-1, 2, 5)
        assertTrue(negative.isOrderedAndUnique { this })
    }

    fun `ordered and unique duplicate`() {
        val duplicated = listOf(1, 2, 2, 3, 5, 8)
        assertFalse(duplicated.isOrderedAndUnique { this })
    }

    fun `ordered and unique out of sequence`() {
        val mixed = listOf(3, 1, 2, 8, 5)
        assertFalse(mixed.isOrderedAndUnique { this })
    }
}