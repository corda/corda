package com.r3corda.core.math

import java.math.BigDecimal

fun Iterable<BigDecimal>.sum(): BigDecimal {
    return this.fold(BigDecimal.valueOf(0)) { a: BigDecimal, b: BigDecimal -> a + b }
}