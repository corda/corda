package net.corda.djvm.serialization.deserializers

import net.corda.core.utilities.NonEmptySet
import java.util.*
import java.util.function.Function

class CreateCollection : Function<Array<Any?>, Collection<Any?>> {
    private val concreteConstructors: Map<Class<out Collection<*>>, (Array<Any?>) -> Collection<Any?>> = mapOf(
        List::class.java to ::createList,
        Set::class.java to ::createSet,
        SortedSet::class.java to ::createSortedSet,
        NavigableSet::class.java to ::createNavigableSet,
        Collection::class.java to ::createCollection,
        NonEmptySet::class.java to ::createNonEmptySet
    )

    private fun createList(values: Array<Any?>): List<Any?> {
        return Collections.unmodifiableList(arrayListOf(*values))
    }

    private fun createSet(values: Array<Any?>): Set<Any?> {
        return Collections.unmodifiableSet(linkedSetOf(*values))
    }

    private fun createSortedSet(values: Array<Any?>): SortedSet<Any?> {
        return Collections.unmodifiableSortedSet(sortedSetOf(*values))
    }

    private fun createNavigableSet(values: Array<Any?>): NavigableSet<Any?> {
        return Collections.unmodifiableNavigableSet(sortedSetOf(*values))
    }

    private fun createCollection(values: Array<Any?>): Collection<Any?> {
        return Collections.unmodifiableCollection(arrayListOf(*values))
    }

    private fun createNonEmptySet(values: Array<Any?>): NonEmptySet<Any?> {
        return NonEmptySet.copyOf(arrayListOf(*values))
    }

    @Suppress("unchecked_cast")
    override fun apply(inputs: Array<Any?>): Collection<Any?> {
        val collectionClass = inputs[0] as Class<out Collection<Any?>>
        val args = inputs[1] as Array<Any?>
        return concreteConstructors[collectionClass]?.invoke(args)!!
    }
}
