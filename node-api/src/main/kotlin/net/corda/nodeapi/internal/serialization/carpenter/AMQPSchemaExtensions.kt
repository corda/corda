package net.corda.nodeapi.internal.serialization.carpenter

import net.corda.nodeapi.internal.serialization.amqp.CompositeType
import net.corda.nodeapi.internal.serialization.amqp.Field as AMQPField
import net.corda.nodeapi.internal.serialization.amqp.Schema as AMQPSchema

fun AMQPSchema.carpenterSchema(
        loaders: List<ClassLoader> = listOf<ClassLoader>(ClassLoader.getSystemClassLoader()))
        : CarpenterSchemas {
    val rtn = CarpenterSchemas.newInstance()

    types.filterIsInstance<CompositeType>().forEach {
        it.carpenterSchema(classLoaders = loaders, carpenterSchemas = rtn)
    }

    return rtn
}

/**
 * if we can load the class then we MUST know about all of it's composite elements
 */
private fun CompositeType.validatePropertyTypes(
        classLoaders: List<ClassLoader> = listOf<ClassLoader>(ClassLoader.getSystemClassLoader())) {
    fields.forEach {
        if (!it.validateType(classLoaders)) throw UncarpentableException(name, it.name, it.type)
    }
}

fun AMQPField.typeAsString() = if (type == "*") requires[0] else type

/**
 * based upon this AMQP schema either
 *  a) add the corresponding carpenter schema to the [carpenterSchemas] param
 *  b) add the class to the dependency tree in [carpenterSchemas] if it cannot be instantiated
 *     at this time
 *
 *  @param classLoaders list of classLoaders, defaulting toe the system class loader, that might
 *  be used to load objects
 *  @param carpenterSchemas structure that holds the dependency tree and list of classes that
 *  need constructing
 *  @param force by default a schema is not added to [carpenterSchemas] if it already exists
 *  on the class path. For testing purposes schema generation can be forced
 */
fun CompositeType.carpenterSchema(
        classLoaders: List<ClassLoader> = listOf<ClassLoader>(ClassLoader.getSystemClassLoader()),
        carpenterSchemas: CarpenterSchemas,
        force: Boolean = false) {
    if (classLoaders.exists(name)) {
        validatePropertyTypes(classLoaders)
        if (!force) return
    }

    val providesList = mutableListOf<Class<*>>()

    var isInterface = false
    var isCreatable = true

    provides.forEach {
        if (name == it) {
            isInterface = true
            return@forEach
        }

        try {
            providesList.add(classLoaders.loadIfExists(it))
        } catch (e: ClassNotFoundException) {
            carpenterSchemas.addDepPair(this, name, it)
            isCreatable = false
        }
    }

    val m: MutableMap<String, Field> = mutableMapOf()

    fields.forEach {
        try {
            m[it.name] = FieldFactory.newInstance(it.mandatory, it.name, it.getTypeAsClass(classLoaders))
        } catch (e: ClassNotFoundException) {
            carpenterSchemas.addDepPair(this, name, it.typeAsString())
            isCreatable = false
        }
    }

    if (isCreatable) {
        carpenterSchemas.carpenterSchemas.add(CarpenterSchemaFactory.newInstance(
                name = name,
                fields = m,
                interfaces = providesList,
                isInterface = isInterface))
    }
}

// map a pair of (typename, mandatory) to the corresponding class type
// where the mandatory AMQP flag maps to the types nullability
val typeStrToType: Map<Pair<String, Boolean>, Class<out Any?>> = mapOf(
        Pair("int", true) to Int::class.javaPrimitiveType!!,
        Pair("int", false) to Integer::class.javaObjectType,
        Pair("short", true) to Short::class.javaPrimitiveType!!,
        Pair("short", false) to Short::class.javaObjectType,
        Pair("long", true) to Long::class.javaPrimitiveType!!,
        Pair("long", false) to Long::class.javaObjectType,
        Pair("char", true) to Char::class.javaPrimitiveType!!,
        Pair("char", false) to java.lang.Character::class.java,
        Pair("boolean", true) to Boolean::class.javaPrimitiveType!!,
        Pair("boolean", false) to Boolean::class.javaObjectType,
        Pair("double", true) to Double::class.javaPrimitiveType!!,
        Pair("double", false) to Double::class.javaObjectType,
        Pair("float", true) to Float::class.javaPrimitiveType!!,
        Pair("float", false) to Float::class.javaObjectType,
        Pair("byte", true) to Byte::class.javaPrimitiveType!!,
        Pair("byte", false) to Byte::class.javaObjectType
)

fun AMQPField.getTypeAsClass(
        classLoaders: List<ClassLoader> = listOf<ClassLoader>(ClassLoader.getSystemClassLoader())
) = typeStrToType[Pair(type, mandatory)] ?: when (type) {
    "string" -> String::class.java
    "*" -> classLoaders.loadIfExists(requires[0])
    else -> classLoaders.loadIfExists(type)
}

fun AMQPField.validateType(
        classLoaders: List<ClassLoader> = listOf<ClassLoader>(ClassLoader.getSystemClassLoader())
) = when (type) {
    "byte", "int", "string", "short", "long", "char", "boolean", "double", "float" -> true
    "*" -> classLoaders.exists(requires[0])
    else -> classLoaders.exists(type)
}

private fun List<ClassLoader>.exists(clazz: String) = this.find {
    try { it.loadClass(clazz); true } catch (e: ClassNotFoundException) { false }
} != null

private fun List<ClassLoader>.loadIfExists(clazz: String): Class<*> {
    this.forEach {
        try {
            return it.loadClass(clazz)
        } catch (e: ClassNotFoundException) {
            return@forEach
        }
    }
    throw ClassNotFoundException(clazz)
}
