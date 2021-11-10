@file:JvmName("Utils")

package net.corda.node.internal.classloading

import net.corda.core.serialization.CustomSerializationScheme
import net.corda.node.internal.ConfigurationException
import net.corda.nodeapi.internal.serialization.CustomSerializationSchemeAdapter
import net.corda.serialization.internal.SerializationScheme
import java.lang.reflect.Constructor

inline fun <reified A : Annotation> Class<*>.requireAnnotation(): A {
    return requireNotNull(getDeclaredAnnotation(A::class.java)) { "$name needs to be annotated with ${A::class.java.name}" }
}

fun scanForCustomSerializationScheme(className: String, classLoader: ClassLoader) : SerializationScheme {
    val schemaClass = try {
        Class.forName(className, false, classLoader)
    } catch (exception: ClassNotFoundException) {
        throw ConfigurationException("$className was declared as a custom serialization scheme but could not be found.")
    }
    val constructor = validateScheme(schemaClass, className)
    return CustomSerializationSchemeAdapter(constructor.newInstance() as CustomSerializationScheme)
}

private fun validateScheme(clazz: Class<*>, className: String): Constructor<*> {
    if (!clazz.interfaces.contains(CustomSerializationScheme::class.java)) {
        throw ConfigurationException("$className was declared as a custom serialization scheme but does not implement" +
                " ${CustomSerializationScheme::class.java.canonicalName}")
    }
    return clazz.constructors.singleOrNull { it.parameters.isEmpty() } ?: throw ConfigurationException("$className was declared as a " +
            "custom serialization scheme but does not have a no argument constructor.")
}