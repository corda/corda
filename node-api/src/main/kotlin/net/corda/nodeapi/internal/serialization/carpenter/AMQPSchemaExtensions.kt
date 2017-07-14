package net.corda.nodeapi.internal.serialization.carpenter

import net.corda.nodeapi.internal.serialization.amqp.CompositeType
import net.corda.nodeapi.internal.serialization.amqp.Field as AMQPField
import net.corda.nodeapi.internal.serialization.amqp.Schema as AMQPSchema

fun AMQPSchema.carpenterSchema(
        loaders : List<ClassLoader> = listOf<ClassLoader>(ClassLoader.getSystemClassLoader()))
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
        classLoaders: List<ClassLoader> = listOf<ClassLoader> (ClassLoader.getSystemClassLoader())){
    fields.forEach {
        if (!it.validateType(classLoaders)) throw UncarpentableException (name, it.name, it.type)
    }
}

fun AMQPField.typeAsString() = if (type =="*") requires[0] else type

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
        classLoaders: List<ClassLoader> = listOf<ClassLoader> (ClassLoader.getSystemClassLoader()),
        carpenterSchemas: CarpenterSchemas,
        force : Boolean = false) {
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
            providesList.add (classLoaders.loadIfExists(it))
        }
        catch (e: ClassNotFoundException) {
            carpenterSchemas.addDepPair(this, name, it)
            isCreatable = false
        }
    }

    val m: MutableMap<String, Field> = mutableMapOf()

    fields.forEach {
        try {
            m[it.name] = FieldFactory.newInstance(it.mandatory, it.name, it.getTypeAsClass(classLoaders))
        }
        catch (e: ClassNotFoundException) {
            carpenterSchemas.addDepPair(this, name, it.typeAsString())
            isCreatable = false
        }
    }

    if (isCreatable) {
        carpenterSchemas.carpenterSchemas.add (CarpenterSchemaFactory.newInstance(
                name = name,
                fields = m,
                interfaces = providesList,
                isInterface = isInterface))
    }
}

fun AMQPField.getTypeAsClass(
        classLoaders: List<ClassLoader> = listOf<ClassLoader> (ClassLoader.getSystemClassLoader())
) = when (type) {
    "int"     -> Int::class.javaPrimitiveType!!
    "string"  -> String::class.java
    "short"   -> Short::class.javaPrimitiveType!!
    "long"    -> Long::class.javaPrimitiveType!!
    "char"    -> Char::class.javaPrimitiveType!!
    "boolean" -> Boolean::class.javaPrimitiveType!!
    "double"  -> Double::class.javaPrimitiveType!!
    "float"   -> Float::class.javaPrimitiveType!!
    "*"       -> classLoaders.loadIfExists(requires[0])
    else      -> classLoaders.loadIfExists(type)
}

fun AMQPField.validateType(
        classLoaders: List<ClassLoader> = listOf<ClassLoader> (ClassLoader.getSystemClassLoader())
) = when (type) {
    "int", "string", "short", "long", "char", "boolean", "double", "float" -> true
    "*"  -> classLoaders.exists(requires[0])
    else -> classLoaders.exists (type)
}

private fun List<ClassLoader>.exists (clazz: String) =
        this.find { try { it.loadClass(clazz); true } catch (e: ClassNotFoundException) { false } } != null

private fun List<ClassLoader>.loadIfExists (clazz: String) : Class<*> {
    this.forEach {
        try {
            return it.loadClass(clazz)
        } catch (e: ClassNotFoundException) {
            return@forEach
        }
    }
    throw ClassNotFoundException(clazz)
}
