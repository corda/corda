@file:JvmName("DJVM")
@file:Suppress("unused")
package sandbox.java.lang

import net.corda.djvm.SandboxRuntimeContext
import net.corda.djvm.analysis.AnalysisConfiguration.Companion.JVM_EXCEPTIONS
import net.corda.djvm.analysis.ExceptionResolver.Companion.getDJVMException
import net.corda.djvm.rules.implementation.*
import org.objectweb.asm.Opcodes.ACC_ENUM
import org.objectweb.asm.Type
import sandbox.isEntryPoint
import sandbox.net.corda.djvm.rules.RuleViolationError

private const val SANDBOX_PREFIX = "sandbox."

fun Any.unsandbox(): Any {
    return when (this) {
        is Object -> fromDJVM()
        is Array<*> -> fromDJVMArray()
        else -> this
    }
}

@Throws(ClassNotFoundException::class)
fun Any.sandbox(): Any {
    return when (this) {
        is kotlin.String -> String.toDJVM(this)
        is kotlin.Char -> Character.toDJVM(this)
        is kotlin.Long -> Long.toDJVM(this)
        is kotlin.Int -> Integer.toDJVM(this)
        is kotlin.Short -> Short.toDJVM(this)
        is kotlin.Byte -> Byte.toDJVM(this)
        is kotlin.Float -> Float.toDJVM(this)
        is kotlin.Double -> Double.toDJVM(this)
        is kotlin.Boolean -> Boolean.toDJVM(this)
        is kotlin.Enum<*> -> toDJVMEnum()
        is kotlin.Throwable -> toDJVMThrowable()
        is Array<*> -> toDJVMArray<Object>()
        else -> this
    }
}

private fun Array<*>.fromDJVMArray(): Array<*> = Object.fromDJVM(this)

/**
 * Use [Class.forName] so that we can also fetch classes for arrays of primitive types.
 * Also use the sandbox's classloader explicitly here, because this invoking class
 * might belong to a shared parent classloader.
 */
@Throws(ClassNotFoundException::class)
internal fun Class<*>.toDJVMType(): Class<*> = Class.forName(name.toSandboxPackage(), false, SandboxRuntimeContext.instance.classLoader)

@Throws(ClassNotFoundException::class)
internal fun Class<*>.fromDJVMType(): Class<*> = Class.forName(name.fromSandboxPackage(), false, SandboxRuntimeContext.instance.classLoader)

private fun kotlin.String.toSandboxPackage(): kotlin.String {
    return if (startsWith(SANDBOX_PREFIX)) {
        this
    } else {
        SANDBOX_PREFIX + this
    }
}

private fun kotlin.String.fromSandboxPackage(): kotlin.String {
    return if (startsWith(SANDBOX_PREFIX)) {
        drop(SANDBOX_PREFIX.length)
    } else {
        this
    }
}

private inline fun <reified T : Object> Array<*>.toDJVMArray(): Array<out T?> {
    @Suppress("unchecked_cast")
    return (java.lang.reflect.Array.newInstance(javaClass.componentType.toDJVMType(), size) as Array<T?>).also {
        for ((i, item) in withIndex()) {
            it[i] = item?.sandbox() as T
        }
    }
}

@Throws(ClassNotFoundException::class)
internal fun Enum<*>.fromDJVMEnum(): kotlin.Enum<*> {
    return javaClass.fromDJVMType().enumConstants[ordinal()] as kotlin.Enum<*>
}

@Throws(ClassNotFoundException::class)
private fun kotlin.Enum<*>.toDJVMEnum(): Enum<*> {
    @Suppress("unchecked_cast")
    return (getEnumConstants(javaClass.toDJVMType() as Class<Enum<*>>) as Array<Enum<*>>)[ordinal]
}

/**
 * Replacement functions for the members of Class<*> that support Enums.
 */
fun isEnum(clazz: Class<*>): kotlin.Boolean
        = (clazz.modifiers and ACC_ENUM != 0) && (clazz.superclass == sandbox.java.lang.Enum::class.java)

fun getEnumConstants(clazz: Class<out Enum<*>>): Array<*>? {
    return getEnumConstantsShared(clazz)?.clone()
}

internal fun enumConstantDirectory(clazz: Class<out Enum<*>>): sandbox.java.util.Map<String, out Enum<*>>? {
    // DO NOT replace get with Kotlin's [] because Kotlin would use java.util.Map.
    @Suppress("ReplaceGetOrSet")
    return allEnumDirectories.get(clazz) ?: createEnumDirectory(clazz)
}

@Suppress("unchecked_cast", "ReplaceGetOrSet")
internal fun getEnumConstantsShared(clazz: Class<out Enum<*>>): Array<out Enum<*>>? {
    return if (isEnum(clazz)) {
        // DO NOT replace get with Kotlin's [] because Kotlin would use java.util.Map.
        allEnums.get(clazz) ?: createEnum(clazz)
    } else {
        null
    }
}

@Suppress("unchecked_cast", "ReplacePutWithAssignment" )
private fun createEnum(clazz: Class<out Enum<*>>): Array<out Enum<*>>? {
    return clazz.getMethod("values").let { method ->
        method.isAccessible = true
        method.invoke(null) as? Array<out Enum<*>>
    // DO NOT replace put with Kotlin's [] because Kotlin would use java.util.Map.
    }?.apply { allEnums.put(clazz, this) }
}

@Suppress("ReplacePutWithAssignment")
private fun createEnumDirectory(clazz: Class<out Enum<*>>): sandbox.java.util.Map<String, out Enum<*>> {
    val universe = getEnumConstantsShared(clazz) ?: throw IllegalArgumentException("${clazz.name} is not an enum type")
    val directory = sandbox.java.util.LinkedHashMap<String, Enum<*>>(2 * universe.size)
    for (entry in universe) {
        // DO NOT replace put with Kotlin's [] because Kotlin would use java.util.Map.
        directory.put(entry.name(), entry)
    }
    // DO NOT replace put with Kotlin's [] because Kotlin would use java.util.Map.
    allEnumDirectories.put(clazz, directory)
    return directory
}

private val allEnums: sandbox.java.util.Map<Class<out Enum<*>>, Array<out Enum<*>>> = sandbox.java.util.LinkedHashMap()
private val allEnumDirectories: sandbox.java.util.Map<Class<out Enum<*>>, sandbox.java.util.Map<String, out Enum<*>>> = sandbox.java.util.LinkedHashMap()

/**
 * Replacement function for Object.hashCode(), because some objects
 * (i.e. arrays) cannot be replaced by [sandbox.java.lang.Object].
 */
fun hashCode(obj: Any?): Int {
    return if (obj is Object) {
        obj.hashCode()
    } else if (obj != null) {
        System.identityHashCode(obj)
    } else {
        // Throw the same exception that the JVM would throw in this case.
        throw NullPointerException().sanitise()
    }
}

/**
 * Replacement functions for Class<*>.forName(...) which protect
 * against users loading classes from outside the sandbox.
 */
@Throws(ClassNotFoundException::class)
fun classForName(className: kotlin.String): Class<*> {
    return Class.forName(toSandbox(className))
}

@Throws(ClassNotFoundException::class)
fun classForName(className: kotlin.String, initialize: kotlin.Boolean, classLoader: ClassLoader): Class<*> {
    return Class.forName(toSandbox(className), initialize, classLoader)
}

/**
 * Force the qualified class name into the sandbox.* namespace.
 * Throw [ClassNotFoundException] anyway if we wouldn't want to
 * return the resulting sandbox class. E.g. for any of our own
 * internal classes.
 */
private fun toSandbox(className: kotlin.String): kotlin.String {
    if (bannedClasses.any { it.matches(className) }) {
        throw ClassNotFoundException(className).sanitise()
    }
    return SANDBOX_PREFIX + className
}

private val bannedClasses = setOf(
    "^java\\.lang\\.DJVM(.*)?\$".toRegex(),
    "^net\\.corda\\.djvm\\..*\$".toRegex(),
    "^Task(.*)?\$".toRegex()
)

/**
 * Exception Management.
 *
 * This function converts a [sandbox.java.lang.Throwable] into a
 * [java.lang.Throwable] that the JVM can actually throw.
 */
fun fromDJVM(t: Throwable?): kotlin.Throwable {
    return if (t is DJVMThrowableWrapper) {
        // We must be exiting a finally block.
        t.fromDJVM()
    } else {
        try {
            /**
             * Someone has created a [sandbox.java.lang.Throwable]
             * and is (re?)throwing it.
             */
            val sandboxedName = t!!.javaClass.name
            if (Type.getInternalName(t.javaClass) in JVM_EXCEPTIONS) {
                // We map these exceptions to their equivalent JVM classes.
                SandboxRuntimeContext.instance.classLoader.loadClass(sandboxedName.fromSandboxPackage())
                        .createJavaThrowable(t)
            } else {
                // Whereas the sandbox creates a synthetic throwable wrapper for these.
                SandboxRuntimeContext.instance.classLoader.loadClass(getDJVMException(sandboxedName))
                    .getDeclaredConstructor(sandboxThrowable)
                    .newInstance(t) as kotlin.Throwable
            }
        } catch (e: Exception) {
            RuleViolationError(e.message).sanitise()
        }
    }
}

/**
 * Wraps a [java.lang.Throwable] inside a [sandbox.java.lang.Throwable].
 * This function is invoked at the beginning of a finally block, and
 * so does not need to return a reference to the equivalent sandboxed
 * exception. The finally block only needs to be able to re-throw the
 * original exception when it finishes.
 */
fun finally(t: kotlin.Throwable): Throwable = DJVMThrowableWrapper(t)

/**
 * Converts a [java.lang.Throwable] into a [sandbox.java.lang.Throwable].
 * It is invoked at the start of each catch block.
 *
 * Note: [DisallowCatchingBlacklistedExceptions] means that we don't
 * need to handle [ThreadDeath] here.
 */
fun catch(t: kotlin.Throwable): Throwable {
    try {
        return t.toDJVMThrowable()
    } catch (e: Exception) {
        throw RuleViolationError(e.message).sanitise()
    }
}

/**
 * Clean up exception stack trace for throwing.
 */
private fun <T: kotlin.Throwable> T.sanitise(): T {
    stackTrace = stackTrace.let {
        it.sliceArray(1 until findEntryPointIndex(it))
    }
    return this
}

/**
 * Worker functions to convert [java.lang.Throwable] into [sandbox.java.lang.Throwable].
 */
private fun kotlin.Throwable.toDJVMThrowable(): Throwable {
    return (this as? DJVMException)?.getThrowable() ?: javaClass.toDJVMType().createDJVMThrowable(this)
}

/**
 * Creates a new [sandbox.java.lang.Throwable] from a [java.lang.Throwable],
 * which was probably thrown by the JVM itself.
 */
private fun Class<*>.createDJVMThrowable(t: kotlin.Throwable): Throwable {
    return (try {
        getDeclaredConstructor(String::class.java).newInstance(String.toDJVM(t.message))
    } catch (e: NoSuchMethodException) {
        newInstance()
    } as Throwable).apply {
        t.cause?.also {
            initCause(it.toDJVMThrowable())
        }
        stackTrace = sanitiseToDJVM(t.stackTrace)
    }
}

private fun Class<*>.createJavaThrowable(t: Throwable): kotlin.Throwable {
    return (try {
        getDeclaredConstructor(kotlin.String::class.java).newInstance(String.fromDJVM(t.message))
    } catch (e: NoSuchMethodException) {
        newInstance()
    } as kotlin.Throwable).apply {
        t.cause?.also {
            initCause(fromDJVM(it))
        }
        stackTrace = copyFromDJVM(t.stackTrace)
    }
}

private fun findEntryPointIndex(source: Array<java.lang.StackTraceElement>): Int {
    var idx = 0
    while (idx < source.size && !isEntryPoint(source[idx])) {
        ++idx
    }
    return idx
}

private fun sanitiseToDJVM(source: Array<java.lang.StackTraceElement>): Array<StackTraceElement> {
    return copyToDJVM(source, 0, findEntryPointIndex(source))
}

internal fun copyToDJVM(source: Array<java.lang.StackTraceElement>, fromIdx: Int, toIdx: Int): Array<StackTraceElement> {
    return source.sliceArray(fromIdx until toIdx).map(::toDJVM).toTypedArray()
}

private fun toDJVM(elt: java.lang.StackTraceElement) = StackTraceElement(
    String.toDJVM(elt.className),
    String.toDJVM(elt.methodName),
    String.toDJVM(elt.fileName),
    elt.lineNumber
)

private fun copyFromDJVM(source: Array<StackTraceElement>): Array<java.lang.StackTraceElement> {
    return source.map(::fromDJVM).toTypedArray()
}

private fun fromDJVM(elt: StackTraceElement) = java.lang.StackTraceElement(
    String.fromDJVM(elt.className),
    String.fromDJVM(elt.methodName),
    String.fromDJVM(elt.fileName),
    elt.lineNumber
)

private val sandboxThrowable: Class<*> = Throwable::class.java
