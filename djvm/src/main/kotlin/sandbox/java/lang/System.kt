@file:Suppress("UNUSED_PARAMETER")

package sandbox.java.lang

import java.io.IOException
import java.util.*

object System {

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

    fun setIn(stream: java.io.InputStream) {}

    fun setOut(stream: java.io.PrintStream) {}

    fun setErr(stream: java.io.PrintStream) {}

    fun console(): java.io.Console? {
        throw NotImplementedError()
    }

    @Throws(java.io.IOException::class)
    fun inheritedChannel(): java.nio.channels.Channel? {
        throw IOException()
    }

    fun setSecurityManager(manager: java.lang.SecurityManager) {}

    fun getSecurityManager(): java.lang.SecurityManager? {
        throw NotImplementedError()
    }

    fun currentTimeMillis(): Long = 0L

    fun nanoTime(): Long = 0L

    fun arraycopy(src: Object, srcPos: Int, dest: Object, destPos: Int, length: Int) {
        java.lang.System.arraycopy(src, srcPos, dest, destPos, length)
    }

    fun identityHashCode(obj: Object): Int {
        val nativeHashCode = java.lang.System.identityHashCode(obj)
        // TODO Instead of using a magic offset below, one could take in a per-context seed
        return objectHashCodes.get().getOrPut(nativeHashCode) {
            val newCounter = objectCounter.get() + 1
            objectCounter.set(newCounter)
            0xfed_c0de + newCounter
        }
    }

    fun getProperties(): java.util.Properties {
        return Properties()
    }

    fun lineSeparator() = "\n"

    fun setProperties(properties: java.util.Properties) {}

    fun getProperty(property: String): String? = null

    fun getProperty(property: String, defaultValue: String): String? = defaultValue

    fun setProperty(property: String, value: String): String? = null

    fun clearProperty(property: String): String? = null

    fun getenv(variable: String): String? = null

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    fun getenv(): java.util.Map<String, String>? = null

    fun exit(exitCode: Int) {
        throw NotImplementedError("Oh, nice try you!")
    }

    fun gc() {}

    fun runFinalization() {}

    fun runFinalizersOnExit(flag: Boolean) {}

    fun load(path: String) {
        throw NotImplementedError("Eh eh, no can do!")
    }

    fun loadLibrary(path: String) {
        throw NotImplementedError("Eh eh, no can do!")
    }

    fun mapLibraryName(path: String): String {
        throw NotImplementedError("Eh eh, no can do!")
    }
}
