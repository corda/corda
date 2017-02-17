package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.util.DefaultClassResolver
import com.esotericsoftware.kryo.util.Util
import net.corda.core.utilities.loggerFor
import java.util.*

fun Kryo.addToWhitelist(type: Class<*>) {
    ((classResolver as CordaClassResolver).whitelist as MutableClassWhitelist).add(type)
}

class CordaClassResolver(val whitelist: ClassWhitelist) : DefaultClassResolver() {

    companion object {
        val logger = loggerFor<CordaClassResolver>()
    }

    override fun registerImplicit(type: Class<*>): Registration {
        val hasAnnotation = checkForAnnotation(type)
        if (!hasAnnotation && !whitelist.isOnTheList(type)) {
            throw KryoException("Class ${Util.className(type)} is not annotated or on the whitelist, so cannot be used in serialisation")
        }
        val references = kryo.references
        try {
            kryo.references = true
            return register(Registration(type, kryo.getDefaultSerializer(type), NAME.toInt()))
        } finally {
            kryo.references = references
        }
    }

    protected fun checkForAnnotation(type: Class<*>): Boolean {
        return type.isAnnotationPresent(CordaSerializable::class.java)
    }
}

interface ClassWhitelist {
    fun isOnTheList(type: Class<*>): Boolean
}

interface MutableClassWhitelist : ClassWhitelist {
    fun add(entry: Class<*>)
}

class EmptyWhitelist : ClassWhitelist {
    override fun isOnTheList(type: Class<*>): Boolean {
        return false
    }
}

class LoggingWhitelist(val delegate: ClassWhitelist, val global: Boolean = true) : MutableClassWhitelist {
    companion object {
        val log = loggerFor<LoggingWhitelist>()
        val globallySeen: MutableSet<Class<*>> = Collections.synchronizedSet(mutableSetOf())
    }

    private val locallySeen: MutableSet<Class<*>> = mutableSetOf()
    private val alreadySeen: MutableSet<Class<*>> get() = if (global) globallySeen else locallySeen

    override fun isOnTheList(type: Class<*>): Boolean {
        if (type !in alreadySeen && !delegate.isOnTheList(type)) {
            alreadySeen += type
            log.info(Util.className(type))
        }
        return true
    }

    override fun add(entry: Class<*>) {
        alreadySeen.add(entry)
    }
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaSerializable
