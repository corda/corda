package net.corda.core.utilities

object SgxSupport {
    val isInsideEnclave: Boolean by lazy {
        System.getProperty("os.name") == "Linux" && (System.getProperty("java.vm.name") == "Avian")
    }
}