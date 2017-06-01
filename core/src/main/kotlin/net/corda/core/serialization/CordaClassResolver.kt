package net.corda.core.serialization

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.util.DefaultClassResolver
import com.esotericsoftware.kryo.util.Util
import net.corda.core.node.AttachmentsClassLoader
import net.corda.core.utilities.loggerFor
import java.io.PrintWriter
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*

fun Kryo.addToWhitelist(type: Class<*>) = ((classResolver as? CordaClassResolver)?.whitelist as? MutableClassList)?.add(type)

fun Kryo.addToBlacklist(type: Class<*>) = ((classResolver as? CordaClassResolver)?.blacklist as? MutableClassList)?.add(type)

/** Support both a whitelist and a blacklist. */
fun makeStandardClassResolver(): ClassResolver {
    return CordaClassResolver(GlobalTransientWhiteList(BuiltInExceptionsClassList()), GlobalTransientBlackList(EmptyClassList))
}

/** Allow everything for serialisation. */
fun makeAcceptAllClassResolver(): ClassResolver {
    return CordaClassResolver(AllClassList, EmptyClassList)
}

/** Allow everything except those in the blacklist. */
fun makeBlackListOnlyClassResolver(): ClassResolver {
    return CordaClassResolver(AllClassList, GlobalTransientBlackList(EmptyClassList))
}

/**
 * Handles class registration for serialisation.
 * It requires a whitelist [ClassList] for those allowed to be serialised.
 * and a blacklist [ClassList] for those not permitted to be serialised.
 */
class CordaClassResolver(val whitelist: ClassList, val blacklist: ClassList) : DefaultClassResolver() {
    /** Returns the registration for the specified class, or null if the class is not registered.  */
    override fun getRegistration(type: Class<*>): Registration? {
        return super.getRegistration(type) ?: checkClass(type)
    }
    // both white and black lists are enabled.
    // TODO: check if separate whitelist and blacklist enable is required.
    private var classListsEnabled = true

    fun disableWhiteAndBlackLists() {
        classListsEnabled = false
    }

    fun enableWhiteAndBlackLists() {
        classListsEnabled = true
    }

    private fun checkClass(type: Class<*>): Registration? {
        /** If call path has disabled whitelisting (see [CordaKryo.register]), just return without checking. */
        if (!classListsEnabled) return null
        // Allow primitives, abstracts and interfaces
        if (type.isPrimitive || type == Any::class.java || Modifier.isAbstract(type.modifiers) || type == String::class.java) return null
        // If array, recurse on element type
        if (type.isArray) {
            return checkClass(type.componentType)
        }
        if (!type.isEnum && Enum::class.java.isAssignableFrom(type)) {
            // Specialised enum entry, so just resolve the parent Enum type since cannot annotate the specialised entry.
            return checkClass(type.superclass)
        }
        // It's safe to have the Class already, since Kryo loads it with initialisation off.
        // First check for blacklisted classes.
        if (blacklist.hasListed(type)) {
            throw KryoException("Class ${Util.className(type)} is blacklisted and cannot be used in serialization")
        }
        // Check for @CordaSerizalizable annotation or whitelisted.
        if (!checkForAnnotation(type) && !whitelist.hasListed(type)) {
            throw KryoException("Class ${Util.className(type)} is not annotated or on the whitelist, so cannot be used in serialization")
        }
        return null
    }

    override fun registerImplicit(type: Class<*>): Registration {
        // We have to set reference to true, since the flag influences how String fields are treated and we want it to be consistent.
        val references = kryo.references
        try {
            kryo.references = true
            return register(Registration(type, kryo.getDefaultSerializer(type), NAME.toInt()))
        } finally {
            kryo.references = references
        }
    }

    // We don't allow the annotation for classes in attachments for now.  The class will be on the main classpath if we have the CorDapp installed.
    // We also do not allow extension of KryoSerializable for annotated classes, or combination with @DefaultSerializer for custom serialisation.
    // TODO: Later we can support annotations on attachment classes and spin up a proxy via bytecode that we know is harmless.
    private fun checkForAnnotation(type: Class<*>): Boolean {
        // First check for @CordaNotSerializable.
        if (type.isAnnotationPresent(CordaNotSerializable::class.java) || hasAnnotationOnInterface(type, CordaNotSerializable::class.java))
            throw KryoException("Class ${Util.className(type)} cannot be used in serialization. " +
                    "This class or at least one of its superclasses or implemented interfaces is annotated as CordaNotSerializable and thus, serialization is not permitted")
        // check for @CordaSerializable.
        if (type.isAnnotationPresent(CordaSerializable::class.java) || hasAnnotationOnInterface(type, CordaSerializable::class.java)) {
            if (type.classLoader is AttachmentsClassLoader)
                throw KryoException("Class ${Util.className(type)} cannot be used in serialization. " +
                        "CordaSerializable annotation for classes in attachments is not permitted")
            if (KryoSerializable::class.java.isAssignableFrom(type))
                throw KryoException("Class ${Util.className(type)} cannot be used in serialization. " +
                        "CordaSerializable annotation for KryoSerializable extensions is not permitted")
            if (type.isAnnotationPresent(DefaultSerializer::class.java))
                throw KryoException("Class ${Util.className(type)} cannot be used in serialization. " +
                        "CordaSerializable and DefaultSerializer annotations cannot be combined")
            return true
        }
        return false
    }

    // Recursively check interfaces.
    private fun hasAnnotationOnInterface(type: Class<*>, annotationClass: Class<out Annotation> ): Boolean {
        return type.interfaces.any {
            it.isAnnotationPresent(annotationClass) || hasAnnotationOnInterface(it, annotationClass)
        } || (type.superclass != null && hasAnnotationOnInterface(type.superclass, annotationClass))
    }

    // Need to clear out class names from attachments.
    override fun reset() {
        super.reset()
        // Kryo creates a cache of class name to Class<*> which does not work so well with multiple class loaders.
        // TODO: come up with a more efficient way.  e.g. segregate the name space by class loader.
        if (nameToClass != null) {
            val classesToRemove: MutableList<String> = ArrayList(nameToClass.size)
            for (entry in nameToClass.entries()) {
                if (entry.value.classLoader is AttachmentsClassLoader) {
                    classesToRemove += entry.key
                }
            }
            for (className in classesToRemove) {
                nameToClass.remove(className)
            }
        }
    }
}

interface ClassList {
    fun hasListed(type: Class<*>): Boolean
}

interface MutableClassList : ClassList {
    fun add(entry: Class<*>)
}

object EmptyClassList : ClassList {
    override fun hasListed(type: Class<*>): Boolean = false
}

class BuiltInExceptionsClassList : ClassList {
    override fun hasListed(type: Class<*>): Boolean = Throwable::class.java.isAssignableFrom(type) && type.`package`.name.startsWith("java.")
}

object AllClassList : ClassList {
    override fun hasListed(type: Class<*>): Boolean = true
}

// TODO: Need some concept of from which class loader
class GlobalTransientWhiteList(val delegate: ClassList) : MutableClassList, ClassList by delegate {
    companion object {
        val whitelist: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    }

    override fun hasListed(type: Class<*>): Boolean {
        if (type.name in whitelist || delegate.hasListed(type))
            return true
        else {
            val aMatch = whitelist.firstOrNull { Class.forName(it).isAssignableFrom(type) }
            if (aMatch != null) {
                whitelist += type.name // add it, so checking is faster next time we encounter this class.
                return true
            }
        }
        return false
    }

    override fun add(entry: Class<*>) {
        whitelist += entry.name
    }
}

// TODO: GlobalTransientBlackList and GlobalTransientWhiteList share the same logic. Consider refactoring.
class GlobalTransientBlackList(val delegate: ClassList) : MutableClassList, ClassList by delegate {
    companion object {
        val blacklist: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    }

    override fun hasListed(type: Class<*>): Boolean {
        if (type.name in blacklist || delegate.hasListed(type))
            return true
        else {
            // TODO: avoid class.forName
            val aMatch = blacklist.firstOrNull { Class.forName(it).isAssignableFrom(type) }
            if (aMatch != null) {
                blacklist += type.name // add it, so checking is faster next time we encounter this class.
                return true
            }
        }
        return false
    }

    override fun add(entry: Class<*>) {
        blacklist += entry.name
    }
}

/**
 * This class is not currently used, but can be installed to log a large number of missing entries from the whitelist
 * and was used to track down the initial set.
 *
 * @suppress
 */
@Suppress("unused")
class LoggingWhitelist(val delegate: ClassList, val global: Boolean = true) : MutableClassList {
    companion object {
        val log = loggerFor<LoggingWhitelist>()
        val globallySeen: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val journalWriter: PrintWriter? = openOptionalDynamicWhitelistJournal()

        private fun openOptionalDynamicWhitelistJournal(): PrintWriter? {
            val fileName = System.getenv("WHITELIST_FILE")
            if (fileName != null && fileName.isNotEmpty()) {
                try {
                    return PrintWriter(Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE), true)
                } catch(ioEx: Exception) {
                    log.error("Could not open/create whitelist journal file for append: $fileName", ioEx)
                }
            }
            return null
        }
    }

    private val locallySeen: MutableSet<String> = mutableSetOf()
    private val alreadySeen: MutableSet<String> get() = if (global) globallySeen else locallySeen

    override fun hasListed(type: Class<*>): Boolean {
        if (type.name !in alreadySeen && !delegate.hasListed(type)) {
            alreadySeen += type.name
            val className = Util.className(type)
            log.warn("Dynamically whitelisted class $className")
            journalWriter?.println(className)
        }
        return true
    }

    override fun add(entry: Class<*>) {
        if (delegate is MutableClassList) {
            delegate.add(entry)
        } else {
            throw UnsupportedOperationException("Cannot add to whitelist since delegate whitelist is not mutable.")
        }
    }
}

