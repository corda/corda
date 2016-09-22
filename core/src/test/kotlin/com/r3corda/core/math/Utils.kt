package com.r3corda.core.math

import org.junit.Test

import org.junit.Assert.*
import java.math.BigDecimal

class Utils {
    @Test
    fun sum() {
        val calculated = listOf(BigDecimal.valueOf(1.0), BigDecimal.valueOf(5.0)).sum()
        assert(calculated == BigDecimal.valueOf(6.0))
    }
}