package net.corda.core.utilities

import net.corda.core.DeleteForDJVM
import java.security.AccessController.doPrivileged
import java.security.PrivilegedAction

@DeleteForDJVM
object SgxSupport {
    @JvmStatic
    val isInsideEnclave: Boolean by lazy {
        doPrivileged(PrivilegedAction {
            (System.getProperty("os.name") == "Linux") && (System.getProperty("java.vm.name") == "Avian (Corda)")
        })
    }
}
