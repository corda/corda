package net.corda.carpenter

import net.corda.core.serialization.carpenter.CarpenterSchemas
import net.corda.core.serialization.carpenter.Schema

import net.corda.core.serialization.amqp.CompositeType

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
