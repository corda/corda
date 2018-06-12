/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.carpenter

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.StubOutForDJVM
import net.corda.serialization.internal.amqp.CompositeType
import net.corda.serialization.internal.amqp.RestrictedType
import net.corda.serialization.internal.amqp.TypeNotation

/**
 * Generated from an AMQP schema this class represents the classes unknown to the deserializer and that thusly
 * require carpenting up in bytecode form. This is a multi step process as carpenting one object may be dependent
 * upon the creation of others, this information is tracked in the dependency tree represented by
 * [dependencies] and [dependsOn]. Creatable classes are stored in [carpenterSchemas].
 *
 * The state of this class after initial generation is expected to mutate as classes are built by the carpenter
 * enabling the resolution of dependencies and thus new carpenter schemas added whilst those already
 * carpented schemas are removed.
 *
 * @property carpenterSchemas The list of carpentable classes
 * @property dependencies Maps a class to a list of classes that depend on it being built first
 * @property dependsOn Maps a class to a list of classes it depends on being built before it
 *
 * Once a class is constructed we can quickly check for resolution by first looking at all of its dependents in the
 * [dependencies] map. This will give us a list of classes that depended on that class being carpented. We can then
 * in turn look up all of those classes in the [dependsOn] list, remove their dependency on the newly created class,
 * and if that list is reduced to zero know we can now generate a [Schema] for them and carpent them up
 */
@KeepForDJVM
data class CarpenterMetaSchema(
        val carpenterSchemas: MutableList<Schema>,
        val dependencies: MutableMap<String, Pair<TypeNotation, MutableList<String>>>,
        val dependsOn: MutableMap<String, MutableList<String>>) {
    companion object CarpenterSchemaConstructor {
        fun newInstance(): CarpenterMetaSchema {
            return CarpenterMetaSchema(mutableListOf(), mutableMapOf(), mutableMapOf())
        }
    }

    fun addDepPair(type: TypeNotation, dependant: String, dependee: String) {
        dependsOn.computeIfAbsent(dependee, { mutableListOf() }).add(dependant)
        dependencies.computeIfAbsent(dependant, { Pair(type, mutableListOf()) }).second.add(dependee)
    }

    val size
        get() = carpenterSchemas.size

    fun isEmpty() = carpenterSchemas.isEmpty()
    fun isNotEmpty() = carpenterSchemas.isNotEmpty()

    // We could make this an abstract method on TypeNotation but that
    // would mean the amqp package being "more" infected with carpenter
    // specific bits.
    @StubOutForDJVM
    fun buildFor(target: TypeNotation, cl: ClassLoader): Unit = when (target) {
        is RestrictedType -> target.carpenterSchema(this)
        is CompositeType -> target.carpenterSchema(cl, this, false)
    }
}

/**
 * Take a dependency tree of [CarpenterMetaSchema] and reduce it to zero by carpenting those classes that
 * require it. As classes are carpented check for dependency resolution, if now free generate a [Schema] for
 * that class and add it to the list of classes ([CarpenterMetaSchema.carpenterSchemas]) that require
 * carpenting
 *
 * @property cc a reference to the actual class carpenter we're using to constuct classes
 * @property objects a list of carpented classes loaded into the carpenters class loader
 */
@DeleteForDJVM
abstract class MetaCarpenterBase(val schemas: CarpenterMetaSchema, val cc: ClassCarpenter) {
    val objects = mutableMapOf<String, Class<*>>()

    fun step(newObject: Schema) {
        objects[newObject.name] = cc.build(newObject)

        // go over the list of everything that had a dependency on the newly
        // carpented class existing and remove it from their dependency list, If that
        // list is now empty we have no impediment to carpenting that class up
        schemas.dependsOn.remove(newObject.name)?.forEach { dependent ->
            require(newObject.name in schemas.dependencies[dependent]!!.second)

            schemas.dependencies[dependent]?.second?.remove(newObject.name)

            // we're out of blockers so  we can now create the type
            if (schemas.dependencies[dependent]?.second?.isEmpty() == true) {
                (schemas.dependencies.remove(dependent)?.first as CompositeType).carpenterSchema(
                        classloader = cc.classloader,
                        carpenterSchemas = schemas)
            }
        }
    }

    abstract fun build()

    val classloader: ClassLoader
        get() = cc.classloader
}

@DeleteForDJVM
class MetaCarpenter(schemas: CarpenterMetaSchema, cc: ClassCarpenter) : MetaCarpenterBase(schemas, cc) {
    override fun build() {
        while (schemas.carpenterSchemas.isNotEmpty()) {
            val newObject = schemas.carpenterSchemas.removeAt(0)
            try {
                step(newObject)
            } catch (e: ClassCarpenterException) {
                throw MetaCarpenterException(newObject.name, e)
            }
        }
    }
}

@DeleteForDJVM
class TestMetaCarpenter(schemas: CarpenterMetaSchema, cc: ClassCarpenter) : MetaCarpenterBase(schemas, cc) {
    override fun build() {
        if (schemas.carpenterSchemas.isEmpty()) return
        step(schemas.carpenterSchemas.removeAt(0))
    }
}

