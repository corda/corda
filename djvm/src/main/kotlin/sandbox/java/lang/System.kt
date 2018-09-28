@file:Suppress("UNUSED_PARAMETER", "UNUSED")

package sandbox.java.lang

import java.io.IOException
import java.util.*

object SystemZZZ {

    private var objectCounter = object : ThreadLocal<Int>() {
        override fun initialValue() = 0
    }

    private var objectHashCodes = object : ThreadLocal<MutableMap<Int, Int>>() {
        override fun initialValue() = mutableMapOf<Int, Int>()
    }

    @JvmField
    val `in`: java.io.InputStream? = null

    @JvmField
    val out: java.io.PrintStream? = null

    @JvmField
    val err: java.io.PrintStream? = null

    @JvmStatic
    fun setIn(stream: java.io.InputStream?) {}

    @JvmStatic
    fun setOut(stream: java.io.PrintStream?) {}

    @JvmStatic
    fun setErr(stream: java.io.PrintStream?) {}

    @JvmStatic
    @Throws(IOException::class)
    fun inheritedChannel(): java.nio.channels.Channel? {
        throw UnsupportedOperationException("No inherited channel support")
    }

    @JvmStatic
    fun setSecurityManager(manager: java.lang.SecurityManager?) {}

    @JvmStatic
    fun getSecurityManager(): java.lang.SecurityManager? = null

    @JvmStatic
    fun arraycopy(src: Any?, srcPos: Int, dest: Any?, destPos: Int, length: Int) {
        java.lang.System.arraycopy(src, srcPos, dest, destPos, length)
    }

    @JvmStatic
    fun identityHashCode(obj: Any?): Int {
        val nativeHashCode = java.lang.System.identityHashCode(obj)
        // TODO Instead of using a magic offset below, one could take in a per-context seed
        return objectHashCodes.get().getOrPut(nativeHashCode) {
            val newCounter = objectCounter.get() + 1
            objectCounter.set(newCounter)
            0xfed_c0de + newCounter
        }
    }

    @JvmStatic
    fun getProperties(): java.util.Properties {
        return Properties()
    }

    @JvmStatic
    fun lineSeparator() = "\n"

    @JvmStatic
    fun setProperties(properties: java.util.Properties) {}

    @JvmStatic
    fun getProperty(property: String): String? = null

    @JvmStatic
    fun getProperty(property: String, defaultValue: String?): String? = defaultValue

    @JvmStatic
    fun setProperty(property: String, value: String?): String? = null

    @JvmStatic
    fun clearProperty(property: String): String? = null

    @JvmStatic
    fun getenv(variable: String): String? = null

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @JvmStatic
    fun getenv(): java.util.Map<String, String>? = null

    @JvmStatic
    fun exit(exitCode: Int) {}

    @JvmStatic
    fun gc() {}

    @JvmStatic
    fun runFinalization() {}

    @JvmStatic
    fun runFinalizersOnExit(flag: Boolean) {}

    @JvmStatic
    fun load(path: String) {}

    @JvmStatic
    fun loadLibrary(path: String) {}

    @JvmStatic
    fun mapLibraryName(path: String): String? = null

}
