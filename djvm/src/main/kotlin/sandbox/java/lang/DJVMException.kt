package sandbox.java.lang

/**
 * All synthetic [Throwable] classes wrapping non-JVM exceptions
 * will implement this interface.
 */
interface DJVMException {
    /**
     * Returns the [sandbox.java.lang.Throwable] instance inside the wrapper.
     */
    fun getThrowable(): Throwable
}