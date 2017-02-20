package net.corda.core.serialization

import com.esotericsoftware.kryo.ClassResolver
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.util.DefaultClassResolver
import com.esotericsoftware.kryo.util.Util
import net.corda.core.node.AttachmentsClassLoader
import net.corda.core.utilities.loggerFor
import java.util.*

fun Kryo.addToWhitelist(type: Class<*>) {
    ((classResolver as? CordaClassResolver)?.whitelist as? MutableClassWhitelist)?.add(type)
}

fun makeStandardClassResolver(): ClassResolver {
    return CordaClassResolver(LoggingWhitelist(GlobalTransientClassWhiteList(EmptyWhitelist())))
}

class CordaClassResolver(val whitelist: ClassWhitelist) : DefaultClassResolver() {

    companion object {
        val logger = loggerFor<CordaClassResolver>()
    }

    /** Returns the registration for the specified class, or null if the class is not registered.  */
    override fun getRegistration(type: Class<*>): Registration? {
        return super.getRegistration(type) ?: checkClass(type)
    }

    private fun checkClass(type: Class<*>): Registration? {
        // Let's see if we are registering
        val stackTrace = Thread.currentThread().stackTrace
        if (stackTrace[3].methodName == "register") {
            // Kryo checks if existing registration when registering.
            return null
        }
        // It's safe to have the Class already, since Kryo loads it with initialisation off.
        val hasAnnotation = checkForAnnotation(type)
        if (!hasAnnotation && !whitelist.hasListed(type)) {
            throw KryoException("Class ${Util.className(type)} is not annotated or on the whitelist, so cannot be used in serialisation")
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

    // We don't allow the annotation for classes in attachments.  The class will be on the main classpath if we have the CorDapp installed.
    protected fun checkForAnnotation(type: Class<*>): Boolean {
        return (type.classLoader !is AttachmentsClassLoader) && type.isAnnotationPresent(CordaSerializable::class.java)
    }
}

interface ClassWhitelist {
    fun hasListed(type: Class<*>): Boolean
}

interface MutableClassWhitelist : ClassWhitelist {
    fun add(entry: Class<*>)
}

class EmptyWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean {
        return false
    }
}

// TODO: Need some concept of from which class loader
class GlobalTransientClassWhiteList(val delegate: ClassWhitelist) : MutableClassWhitelist, ClassWhitelist by delegate {
    companion object {
        val whitelist: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    }

    override fun hasListed(type: Class<*>): Boolean {
        return (type.name in whitelist) || delegate.hasListed(type)
    }

    override fun add(entry: Class<*>) {
        whitelist += entry.name
    }
}

// TODO: Need some concept of from which class loader
class LoggingWhitelist(val delegate: ClassWhitelist, val global: Boolean = true) : MutableClassWhitelist {
    companion object {
        val log = loggerFor<LoggingWhitelist>()
        val globallySeen: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    }

    private val locallySeen: MutableSet<String> = mutableSetOf()
    private val alreadySeen: MutableSet<String> get() = if (global) globallySeen else locallySeen

    override fun hasListed(type: Class<*>): Boolean {
        if (type.name !in alreadySeen && !delegate.hasListed(type)) {
            alreadySeen += type.name
            log.info("Dynamically whitelisted class ${Util.className(type)}")
        }
        return true
    }

    override fun add(entry: Class<*>) {
        if (delegate is MutableClassWhitelist) {
            delegate.add(entry)
        } else {
            throw UnsupportedOperationException("Cannot add to whitelist since delegate whitelist is not mutable.")
        }
    }
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaSerializable
