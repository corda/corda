/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.serialization.kryo

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.esotericsoftware.kryo.util.DefaultClassResolver
import com.esotericsoftware.kryo.util.Util
import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.internal.writer
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.utilities.contextLogger
import net.corda.serialization.internal.AttachmentsClassLoader
import net.corda.serialization.internal.MutableClassWhitelist
import net.corda.serialization.internal.TransientClassWhiteList
import net.corda.serialization.internal.amqp.hasCordaSerializable
import java.io.PrintWriter
import java.lang.reflect.Modifier.isAbstract
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.*
import java.util.*

/**
 * Corda specific class resolver which enables extra customisation for the purposes of serialization using Kryo
 */
class CordaClassResolver(serializationContext: SerializationContext) : DefaultClassResolver() {
    val whitelist: ClassWhitelist = TransientClassWhiteList(serializationContext.whitelist)

    // These classes are assignment-compatible Java equivalents of Kotlin classes.
    // The point is that we do not want to send Kotlin types "over the wire" via RPC.
    private val javaAliases: Map<Class<*>, Class<*>> = mapOf(
            listOf<Any>().javaClass to Collections.emptyList<Any>().javaClass,
            setOf<Any>().javaClass to Collections.emptySet<Any>().javaClass,
            mapOf<Any, Any>().javaClass to Collections.emptyMap<Any, Any>().javaClass
    )

    private fun typeForSerializationOf(type: Class<*>): Class<*> = javaAliases[type] ?: type

    /** Returns the registration for the specified class, or null if the class is not registered.  */
    override fun getRegistration(type: Class<*>): Registration? {
        val targetType = typeForSerializationOf(type)
        return super.getRegistration(targetType) ?: checkClass(targetType)
    }

    private var whitelistEnabled = true

    fun disableWhitelist() {
        whitelistEnabled = false
    }

    fun enableWhitelist() {
        whitelistEnabled = true
    }

    private fun checkClass(type: Class<*>): Registration? {
        // If call path has disabled whitelisting (see [CordaKryo.register]), just return without checking.
        if (!whitelistEnabled) return null
        // If array, recurse on element type
        if (type.isArray) return checkClass(type.componentType)
        // Specialised enum entry, so just resolve the parent Enum type since cannot annotate the specialised entry.
        if (!type.isEnum && Enum::class.java.isAssignableFrom(type)) return checkClass(type.superclass)
        // Allow primitives, abstracts and interfaces. Note that we can also create abstract Enum types,
        // but we don't want to whitelist those here.
        if (type.isPrimitive || type == Any::class.java || type == String::class.java || (!type.isEnum && isAbstract(type.modifiers))) return null
        // It's safe to have the Class already, since Kryo loads it with initialisation off.
        // If we use a whitelist with blacklisting capabilities, whitelist.hasListed(type) may throw an IllegalStateException if input class is blacklisted.
        // Thus, blacklisting precedes annotation checking.
        if (!whitelist.hasListed(type) && !checkForAnnotation(type)) {
            throw KryoException("Class ${Util.className(type)} is not annotated or on the whitelist, so cannot be used in serialization")
        }
        return null
    }

    override fun registerImplicit(type: Class<*>): Registration {
        val targetType = typeForSerializationOf(type)
        val objectInstance = targetType.kotlinObjectInstance

        // We have to set reference to true, since the flag influences how String fields are treated and we want it to be consistent.
        val references = kryo.references
        try {
            kryo.references = true
            val serializer = when {
                objectInstance != null -> KotlinObjectSerializer(objectInstance)
                kotlin.jvm.internal.Lambda::class.java.isAssignableFrom(targetType) -> // Kotlin lambdas extend this class and any captured variables are stored in synthetic fields
                    FieldSerializer<Any>(kryo, targetType).apply { setIgnoreSyntheticFields(false) }
                Throwable::class.java.isAssignableFrom(targetType) -> ThrowableSerializer(kryo, targetType)
                else -> kryo.getDefaultSerializer(targetType)
            }
            return register(Registration(targetType, serializer, NAME.toInt()))
        } finally {
            kryo.references = references
        }
    }

    override fun writeName(output: Output, type: Class<*>, registration: Registration) {
        super.writeName(output, registration.type ?: type, registration)
    }

    // Trivial Serializer which simply returns the given instance, which we already know is a Kotlin object
    private class KotlinObjectSerializer(private val objectInstance: Any) : Serializer<Any>() {
        override fun read(kryo: Kryo, input: Input, type: Class<Any>): Any = objectInstance
        override fun write(kryo: Kryo, output: Output, obj: Any) = Unit
    }

    // We don't allow the annotation for classes in attachments for now.  The class will be on the main classpath if we have the CorDapp installed.
    // We also do not allow extension of KryoSerializable for annotated classes, or combination with @DefaultSerializer for custom serialisation.
    // TODO: Later we can support annotations on attachment classes and spin up a proxy via bytecode that we know is harmless.
    private fun checkForAnnotation(type: Class<*>): Boolean {
        return (type.classLoader !is AttachmentsClassLoader)
                && !KryoSerializable::class.java.isAssignableFrom(type)
                && !type.isAnnotationPresent(DefaultSerializer::class.java)
                && hasCordaSerializable(type)
    }

    // Need to clear out class names from attachments.
    override fun reset() {
        super.reset()
        // Kryo creates a cache of class name to Class<*> which does not work so well with multiple class loaders.
        // TODO: come up with a more efficient way.  e.g. segregate the name space by class loader.
        if (nameToClass != null) {
            val classesToRemove: MutableList<String> = ArrayList(nameToClass.size)
            nameToClass.entries()
                    .filter { it.value.classLoader is AttachmentsClassLoader }
                    .forEach { classesToRemove += it.key }
            for (className in classesToRemove) {
                nameToClass.remove(className)
            }
        }
    }
}

/**
 * This class is not currently used, but can be installed to log a large number of missing entries from the whitelist
 * and was used to track down the initial set.
 */
@Suppress("unused")
class LoggingWhitelist(val delegate: ClassWhitelist, val global: Boolean = true) : MutableClassWhitelist {
    companion object {
        private val log = contextLogger()
        val globallySeen: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val journalWriter: PrintWriter? = openOptionalDynamicWhitelistJournal()

        private fun openOptionalDynamicWhitelistJournal(): PrintWriter? {
            val fileName = System.getenv("WHITELIST_FILE")
            if (fileName != null && fileName.isNotEmpty()) {
                try {
                    return PrintWriter(Paths.get(fileName).writer(UTF_8, CREATE, APPEND, WRITE), true)
                } catch (ioEx: Exception) {
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
        if (delegate is MutableClassWhitelist) {
            delegate.add(entry)
        } else {
            throw UnsupportedOperationException("Cannot add to whitelist since delegate whitelist is not mutable.")
        }
    }
}

