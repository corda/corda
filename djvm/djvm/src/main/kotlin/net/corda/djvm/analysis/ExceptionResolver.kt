package net.corda.djvm.analysis

import org.objectweb.asm.Type

class ExceptionResolver(
    private val jvmExceptionClasses: Set<String>,
    private val pinnedClasses: Set<String>,
    private val sandboxPrefix: String
) {
    companion object {
        private const val DJVM_EXCEPTION_NAME = "\$1DJVM"

        fun isDJVMException(className: String): Boolean = className.endsWith(DJVM_EXCEPTION_NAME)
        fun getDJVMException(className: String): String = className + DJVM_EXCEPTION_NAME
        fun getDJVMExceptionOwner(className: String): String = className.dropLast(DJVM_EXCEPTION_NAME.length)
    }

    fun getThrowableName(clazz: Class<*>): String {
        return getDJVMException(Type.getInternalName(clazz))
    }

    fun getThrowableSuperName(clazz: Class<*>): String {
        return getThrowableOwnerName(Type.getInternalName(clazz.superclass))
    }

    fun getThrowableOwnerName(className: String): String {
        return if (className in jvmExceptionClasses) {
            className.unsandboxed
        } else if (className in pinnedClasses) {
            className
        } else {
            getDJVMException(className)
        }
    }

    private val String.unsandboxed: String get() = if (startsWith(sandboxPrefix)) {
        drop(sandboxPrefix.length)
    } else {
        this
    }
}