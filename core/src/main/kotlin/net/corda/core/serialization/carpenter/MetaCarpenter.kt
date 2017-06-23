package net.corda.core.serialization.carpenter

import net.corda.core.serialization.amqp.TypeNotation

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
