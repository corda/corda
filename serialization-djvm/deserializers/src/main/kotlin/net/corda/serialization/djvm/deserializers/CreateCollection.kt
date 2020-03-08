package net.corda.serialization.djvm.deserializers

import net.corda.core.utilities.NonEmptySet
import java.util.Collections.unmodifiableCollection
import java.util.Collections.unmodifiableList
import java.util.Collections.unmodifiableNavigableSet
import java.util.Collections.unmodifiableSet
import java.util.Collections.unmodifiableSortedSet
import java.util.NavigableSet
import java.util.SortedSet
import java.util.TreeSet
import java.util.function.Function

class CreateCollection : Function<Array<out Any?>, Collection<Any?>> {
    private val concreteConstructors: Map<Class<out Collection<*>>, (Array<out Any?>) -> Collection<Any?>> = mapOf(
        List::class.java to ::createList,
        Set::class.java to ::createSet,
        SortedSet::class.java to ::createSortedSet,
        NavigableSet::class.java to ::createNavigableSet,
        Collection::class.java to ::createCollection,
        NonEmptySet::class.java to ::createNonEmptySet
    )

    private fun createList(values: Array<out Any?>): List<Any?> {
        return unmodifiableList(values.toCollection(ArrayList()))
    }

    private fun createSet(values: Array<out Any?>): Set<Any?> {
        return unmodifiableSet(values.toCollection(LinkedHashSet()))
    }

    private fun createSortedSet(values: Array<out Any?>): SortedSet<Any?> {
        return unmodifiableSortedSet(values.toCollection(TreeSet()))
    }

    private fun createNavigableSet(values: Array<out Any?>): NavigableSet<Any?> {
        return unmodifiableNavigableSet(values.toCollection(TreeSet()))
    }

    private fun createCollection(values: Array<out Any?>): Collection<Any?> {
        return unmodifiableCollection(values.toCollection(ArrayList()))
    }

    private fun createNonEmptySet(values: Array<out Any?>): NonEmptySet<Any?> {
        return NonEmptySet.copyOf(values.toCollection(ArrayList()))
    }

    @Suppress("unchecked_cast")
    override fun apply(inputs: Array<out Any?>): Collection<Any?> {
        val collectionClass = inputs[0] as Class<out Collection<Any?>>
        val args = inputs[1] as Array<out Any?>
        return concreteConstructors[collectionClass]?.invoke(args)!!
    }
}
