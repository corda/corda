package net.corda.core.utilities

import net.corda.core.KeepForDJVM

@KeepForDJVM
object SgxSupport {
    @JvmStatic
    val isInsideEnclave: Boolean = true
}
