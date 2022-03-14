package net.corda.core.utilities

import net.corda.core.DeleteForDJVM

//@DeleteForDJVM
object SgxSupport {
    @JvmStatic
    val isInsideEnclave: Boolean by lazy {
        (System.getProperty("os.name") == "linuxsgx") && (System.getProperty("java.vm.name") == "Substrate VM")
    }
}
