package net.corda.core.serialization.carpenter

import net.corda.core.serialization.amqp.CompositeType
import net.corda.core.serialization.amqp.TypeNotation

/**********************************************************************************************************************/

data class CarpenterSchemas (
        val carpenterSchemas : MutableList<Schema>,
        val dependencies     : MutableMap<String, Pair<TypeNotation, MutableList<String>>>,
        val dependsOn        : MutableMap<String, MutableList<String>>) {
    companion object CarpenterSchemaConstructor {
        fun newInstance(): CarpenterSchemas {
            return CarpenterSchemas(
                    mutableListOf<Schema>(),
                    mutableMapOf<String, Pair<TypeNotation, MutableList<String>>>(),
                    mutableMapOf<String, MutableList<String>>())
        }
    }

    fun addDepPair(type: TypeNotation, dependant: String, dependee: String) {
        fun String.name() = this.split ('.').last().split('$').last()
        println ("add dep ${dependant.name()} on ${dependee.name()}")
        dependsOn.computeIfAbsent(dependee, { mutableListOf<String>() }).add(dependant)
        dependencies.computeIfAbsent(dependant, { Pair(type, mutableListOf<String>()) }).second.add(dependee)
    }

    val size
        get() = carpenterSchemas.size
}

/**********************************************************************************************************************/

abstract class MetaCarpenterBase (val schemas : CarpenterSchemas) {

    private val cc = ClassCarpenter()
    val objects = mutableMapOf<String, Class<*>>()

    fun step (newObject : Schema) {
        objects[newObject.name] = cc.build (newObject)

        /* go over the list of everything that had a dependency on the newly
           carpented class existing and remove it from their dependency list, If that
           list is now empty we have no impediment to carpenting that class up */
        schemas.dependsOn.remove(newObject.name)?.forEach { dependent ->
            assert (newObject.name in schemas.dependencies[dependent]!!.second)

            schemas.dependencies[dependent]?.second?.remove(newObject.name)

            /* we're out of blockers so  we can now create the type */
            if (schemas.dependencies[dependent]?.second?.isEmpty() ?: false) {
                (schemas.dependencies.remove (dependent)?.first as CompositeType).carpenterSchema (
                        classLoaders = listOf<ClassLoader> (
                                ClassLoader.getSystemClassLoader(),
                                cc.classloader),
                        carpenterSchemas = schemas)
            }
        }
    }

    abstract fun build()
}

/**********************************************************************************************************************/

class MetaCarpenter (schemas : CarpenterSchemas) : MetaCarpenterBase (schemas) {
    override fun build() {
        while (schemas.carpenterSchemas.isNotEmpty()) {
            val newObject = schemas.carpenterSchemas.removeAt(0)
            step (newObject)
        }
    }
}

/**********************************************************************************************************************/

class TestMetaCarpenter (schemas : CarpenterSchemas) : MetaCarpenterBase (schemas) {
    override fun build() {
        println ("TestMetaCarpenter::build")
        if (schemas.carpenterSchemas.isEmpty()) return
        step (schemas.carpenterSchemas.removeAt(0))
    }
}

/**********************************************************************************************************************/
