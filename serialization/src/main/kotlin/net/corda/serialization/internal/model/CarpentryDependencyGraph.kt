package net.corda.serialization.internal.model

import java.io.NotSerializableException
import java.lang.reflect.Type

/**
 * Once we have the complete graph of types requiring carpentry to hand, we can use it to sort those types in reverse-
 * dependency order, i.e. beginning with those types that have no dependencies on other types, then the types that
 * depended on those types, and so on. This means we can feed types directly to the [RemoteTypeCarpenter], and don't
 * have to use the [CarpenterMetaSchema].
 *
 * @param typesRequiringCarpentry The set of [RemoteTypeInformation] for types that are not reachable by the current
 * classloader.
 */
class CarpentryDependencyGraph private constructor(private val typesRequiringCarpentry: Set<RemoteTypeInformation>) {

    companion object {
        /**
         * Sort the [typesRequiringCarpentry] into reverse-dependency order, then pass them to the provided
         * [Type]-builder, collating the results into a [Map] of [Type] by [TypeIdentifier]
         */
        fun buildInReverseDependencyOrder(
                typesRequiringCarpentry: Set<RemoteTypeInformation>,
                getOrBuild: (RemoteTypeInformation) -> Type): Map<TypeIdentifier, Type> =
            CarpentryDependencyGraph(typesRequiringCarpentry.toSet()).buildInOrder(getOrBuild)
    }

    /**
     * A map of inbound edges by node.
     *
     * A [RemoteTypeInformation] map key is a type that requires other types to have been constructed before it can be
     * constructed.
     *
     * Each [RemoteTypeInformation] in the corresponding [Set] map value is one of the types that the key-type depends on.
     *
     * No key ever maps to an empty set: types with no dependencies are not included in this map.
     */
    private val dependencies = mutableMapOf<RemoteTypeInformation, MutableSet<RemoteTypeInformation>>()

    /**
     * If it is in [typesRequiringCarpentry], then add an edge from [dependee] to this type to the [dependencies] graph.
     */
    private fun RemoteTypeInformation.dependsOn(dependee: RemoteTypeInformation) = dependsOn(listOf(dependee))

    /**
     * Add an edge from each of these [dependees] that are in [typesRequiringCarpentry] to this type to the
     * [dependencies] graph.
     */
    private fun RemoteTypeInformation.dependsOn(dependees: Collection<RemoteTypeInformation>) {
        val dependeesInTypesRequiringCarpentry = dependees.filter { it in typesRequiringCarpentry}
        if (dependeesInTypesRequiringCarpentry.isEmpty()) return // we don't want to put empty sets into the map.
            dependencies.compute(this) { _, dependees ->
                dependees?.apply { addAll(dependeesInTypesRequiringCarpentry) } ?:
                dependeesInTypesRequiringCarpentry.toMutableSet()
            }
    }

    /**
     * Traverses each of the [typesRequiringCarpentry], building (or obtaining from a cache) the corresponding [Type]
     * and populating them into a [Map] of [Type] by [TypeIdentifier].
     */
    private fun buildInOrder(getOrBuild: (RemoteTypeInformation) -> Type): Map<TypeIdentifier, Type> {
        typesRequiringCarpentry.forEach { it.recordDependencies() }

        return topologicalSort(typesRequiringCarpentry).associate { information ->
            information.typeIdentifier to getOrBuild(information)
        }
    }

    /**
     * Record appropriate dependencies for each type of [RemoteTypeInformation]
     */
    private fun RemoteTypeInformation.recordDependencies() = when (this) {
        is RemoteTypeInformation.Composable -> {
            dependsOn(typeParameters)
            dependsOn(interfaces)
            dependsOn(properties.values.map { it.type })
        }
        is RemoteTypeInformation.AnInterface -> {
            dependsOn(typeParameters)
            dependsOn(interfaces)
            dependsOn(properties.values.map { it.type })
        }
        is RemoteTypeInformation.AnArray -> dependsOn(componentType)
        is RemoteTypeInformation.Parameterised -> dependsOn(typeParameters)
        else -> {}
    }

    /**
     * Separate out those [types] which have [noDependencies] from those which still have dependencies.
     *
     * Remove the types with no dependencies from the graph, identifying which types are left with no inbound dependees
     * as a result, then return the types with no dependencies concatenated with the [topologicalSort] of the remaining
     * types, minus the newly-independent types.
     */
    private fun topologicalSort(
            types: Set<RemoteTypeInformation>,
            noDependencies: Set<RemoteTypeInformation> = types - dependencies.keys): Sequence<RemoteTypeInformation> {
        // Types which still have dependencies.
        val remaining = dependencies.keys.toSet()

        // Remove the types which have no dependencies from the dependencies of the remaining types, and identify
        // those types which have no dependencies left after we've done this.
        val newlyIndependent = dependencies.asSequence().mapNotNull { (dependent, dependees) ->
            dependees.removeAll(noDependencies)
            if (dependees.isEmpty()) dependent else null
        }.toSet()

        // If there are still types with dependencies, and we have no dependencies we can remove, then we can't continue.
        if (newlyIndependent.isEmpty() && dependencies.isNotEmpty()) {
            throw NotSerializableException(
                    "Cannot build dependencies for " +
                            dependencies.keys.map { it.typeIdentifier.prettyPrint(false) })
        }

        // Remove the types which have no dependencies remaining, maintaining the invariant that no key maps to an
        // empty set.
        dependencies.keys.removeAll(newlyIndependent)

        // Return the types that had no dependencies, then recurse to process the remainder.
        return noDependencies.asSequence() +
                if (dependencies.isEmpty()) newlyIndependent.asSequence() else topologicalSort(remaining, newlyIndependent)
    }
}