/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("ObservableUtilities")

package net.corda.client.jfx.utils

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import javafx.collections.transformation.FilteredList
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.DataFeed
import net.corda.core.node.services.Vault
import org.fxmisc.easybind.EasyBind
import rx.Observable
import rx.schedulers.Schedulers
import java.util.function.Predicate

/**
 * Here follows utility extension functions that help reduce the visual load when developing RX code. Each function should
 * have a short accompanying example code.
 */

/**
 * val person: ObservableValue<Person> = (..)
 * val personName: ObservableValue<String> = person.map { it.name }
 */
fun <A, B> ObservableValue<out A>.map(function: (A) -> B): ObservableValue<B> = EasyBind.map(this, function)

/**
 * val dogs: ObservableList<Dog> = (..)
 * val dogOwners: ObservableList<Person> = dogs.map { it.owner }
 *
 * @param cached If true the results of the mapped function are cached in a backing list. If false each get() will
 *     re-run the function.
 */
fun <A, B> ObservableList<out A>.map(cached: Boolean = true, function: (A) -> B): ObservableList<B> {
    return if (cached) {
        MappedList(this, function)
    } else {
        EasyBind.map(this, function)
    }
}

/**
 * val aliceHeight: ObservableValue<Long> = (..)
 * val bobHeight: ObservableValue<Long> = (..)
 * fun sumHeight(a: Long, b: Long): Long { .. }
 *
 * val aliceBobSumHeight = ::sumHeight.lift(aliceHeight, bobHeight)
 * val aliceHeightPlus2 = ::sumHeight.lift(aliceHeight, 2L.lift())
 */
fun <A> A.lift(): ObservableValue<A> = ReadOnlyObjectWrapper(this)

fun <A, R> ((A) -> R).lift(
        arg0: ObservableValue<A>
): ObservableValue<R> = EasyBind.map(arg0, this)

fun <A, B, R> ((A, B) -> R).lift(
        arg0: ObservableValue<A>,
        arg1: ObservableValue<B>
): ObservableValue<R> = EasyBind.combine(arg0, arg1, this)

fun <A, B, C, R> ((A, B, C) -> R).lift(
        arg0: ObservableValue<A>,
        arg1: ObservableValue<B>,
        arg2: ObservableValue<C>
): ObservableValue<R> = EasyBind.combine(arg0, arg1, arg2, this)

fun <A, B, C, D, R> ((A, B, C, D) -> R).lift(
        arg0: ObservableValue<A>,
        arg1: ObservableValue<B>,
        arg2: ObservableValue<C>,
        arg3: ObservableValue<D>
): ObservableValue<R> = EasyBind.combine(arg0, arg1, arg2, arg3, this)

/**
 * data class Person(val height: ObservableValue<Long>)
 * val person: ObservableValue<Person> = (..)
 * val personHeight: ObservableValue<Long> = person.bind { it.height }
 */
fun <A, B> ObservableValue<out A>.bind(function: (A) -> ObservableValue<B>): ObservableValue<B> =
        EasyBind.monadic(this).flatMap(function)

/**
 * A variant of [bind] that has out variance on the output type. This is sometimes useful when kotlin is too eager to
 * propagate variance constraints and type inference fails.
 */
fun <A, B> ObservableValue<out A>.bindOut(function: (A) -> ObservableValue<out B>): ObservableValue<out B> =
        EasyBind.monadic(this).flatMap(uncheckedCast(function))

/**
 * enum class FilterCriterion { HEIGHT, NAME }
 * val filterCriterion: ObservableValue<FilterCriterion> = (..)
 * val people: ObservableList<Person> = (..)
 * fun filterFunction(filterCriterion: FilterCriterion): (Person) -> Boolean { .. }
 *
 * val filteredPeople: ObservableList<Person> = people.filter(filterCriterion.map(filterFunction))
 */
fun <A> ObservableList<out A>.filter(predicate: ObservableValue<(A) -> Boolean>): ObservableList<A> {
    // We cast here to enforce variance, FilteredList should be covariant
    return FilteredList<A>(uncheckedCast(this)).apply {
        predicateProperty().bind(predicate.map { predicateFunction ->
            Predicate<A> { predicateFunction(it) }
        })
    }
}

/**
 * data class Dog(val owner: Person?)
 * val dogs: ObservableList<Dog> = (..)
 * val owners: ObservableList<Person> = dogs.map(Dog::owner).filterNotNull()
 */
fun <A> ObservableList<out A?>.filterNotNull(): ObservableList<A> {
    //TODO This is a tactical work round for an issue with SAM conversion (https://youtrack.jetbrains.com/issue/ALL-1552) so that the M10 explorer works.
    return uncheckedCast(uncheckedCast<Any, ObservableList<A?>>(this).filtered { t -> t != null })
}

/**
 * val people: ObservableList<Person> = (..)
 * val concatenatedNames = people.foldObservable("", { names, person -> names + person.name })
 * val concatenatedNames2 = people.map(Person::name).fold("", String::plus)
 */
fun <A, B> ObservableList<out A>.foldObservable(initial: B, folderFunction: (B, A) -> B): ObservableValue<B> {
    return Bindings.createObjectBinding({
        var current = initial
        forEach {
            current = folderFunction(current, it)
        }
        current
    }, arrayOf(this))
}

/**
 * data class Person(val height: ObservableValue<Long>)
 * val people: ObservableList<Person> = (..)
 * val heights: ObservableList<Long> = people.map(Person::height).flatten()
 */
fun <A> ObservableList<out ObservableValue<out A>>.flatten(): ObservableList<A> = FlattenedList(this)

/**
 * data class Person(val height: ObservableValue<Long>)
 * val people: List<Person> = listOf(alice, bob)
 * val heights: ObservableList<Long> = people.map(Person::height).sequence()
 */
fun <A> Collection<ObservableValue<out A>>.sequence(): ObservableList<A> = FlattenedList(FXCollections.observableArrayList(this))

/**
 * data class Person(val height: Long)
 * val people: ObservableList<Person> = (..)
 * val nameToHeight: ObservableMap<String, Long> = people.associateBy(Person::name) { name, person -> person.height }
 */
fun <K, A, B> ObservableList<out A>.associateBy(toKey: (A) -> K, assemble: (K, A) -> B): ObservableMap<K, B> {
    return AssociatedList(this, toKey, assemble)
}

/**
 * val people: ObservableList<Person> = (..)
 * val nameToPerson: ObservableMap<String, Person> = people.associateBy(Person::name)
 */
fun <K, A> ObservableList<out A>.associateBy(toKey: (A) -> K): ObservableMap<K, A> {
    return associateBy(toKey) { _, value -> value }
}

/**
 * val people: ObservableList<Person> = (..)
 * val heightToNames: ObservableMap<Long, ObservableList<String>> = people.associateByAggregation(Person::height) { name, person -> person.name }
 */
fun <K : Any, A : Any, B> ObservableList<out A>.associateByAggregation(toKey: (A) -> K, assemble: (K, A) -> B): ObservableMap<K, ObservableList<B>> {
    return AssociatedList(AggregatedList(this, toKey) { key, members -> Pair(key, members) }, { it.first }) { key, pair ->
        pair.second.map { assemble(key, it) }
    }
}

/**
 * val people: ObservableList<Person> = (..)
 * val heightToPeople: ObservableMap<Long, ObservableList<Person>> = people.associateByAggregation(Person::height)
 */
fun <K : Any, A : Any> ObservableList<out A>.associateByAggregation(toKey: (A) -> K): ObservableMap<K, ObservableList<A>> {
    return associateByAggregation(toKey) { _, value -> value }
}

/**
 * val nameToPerson: ObservableMap<String, Person> = (..)
 * val john: ObservableValue<Person?> = nameToPerson.getObservableValue("John")
 */
fun <K, V> ObservableMap<K, V>.getObservableValue(key: K): ObservableValue<V?> {
    val property = SimpleObjectProperty(get(key))
    addListener { change: MapChangeListener.Change<out K, out V> ->
        if (change.key == key) {
            // This is true both when a fresh element was inserted and when an existing was updated
            if (change.wasAdded()) {
                property.set(change.valueAdded)
            } else if (change.wasRemoved()) {
                property.set(null)
            }
        }
    }
    return property
}

/**
 * val nameToPerson: ObservableMap<String, Person> = (..)
 * val people: ObservableList<Person> = nameToPerson.getObservableValues()
 */
fun <K, V> ObservableMap<K, V>.getObservableValues(): ObservableList<V> {
    return MapValuesList.create(this) { it.value }
}

/**
 * val nameToPerson: ObservableMap<String, Person> = (..)
 * val people: ObservableList<Person> = nameToPerson.getObservableValues()
 */
fun <K, V> ObservableMap<K, V>.getObservableEntries(): ObservableList<Map.Entry<K, V>> {
    return MapValuesList.create(this) { it }
}

/**
 * val groups: ObservableList<ObservableList<Person>> = (..)
 * val allPeople: ObservableList<Person> = groups.concatenate()
 */
fun <A> ObservableList<ObservableList<A>>.concatenate(): ObservableList<A> {
    return ConcatenatedList(this)
}

/**
 * data class Person(val name: String, val managerName: String)
 * val people: ObservableList<Person> = (..)
 * val managerEmployeeMapping: ObservableList<Pair<Person, ObservableList<Person>>> =
 *   people.leftOuterJoin(people, Person::name, Person::managerName) { manager, employees -> Pair(manager, employees) }
 */
fun <A : Any, B : Any, C, K : Any> ObservableList<A>.leftOuterJoin(
        rightTable: ObservableList<B>,
        leftToJoinKey: (A) -> K,
        rightToJoinKey: (B) -> K,
        assemble: (A, ObservableList<B>) -> C
): ObservableList<C> {
    val joinedMap = leftOuterJoin(rightTable, leftToJoinKey, rightToJoinKey)
    return joinedMap.getObservableValues().map { (first, second) ->
        first.map { assemble(it, second) }
    }.concatenate()
}

/**
 * data class Person(name: String, favouriteSpecies: Species)
 * data class Animal(name: String, species: Species)
 * val people: ObservableList<Person> = (..)
 * val animals: ObservableList<Animal> = (..)
 * val peopleToFavouriteAnimals: ObservableMap<Species, Pair<ObservableList<Person>, ObservableList<Animal>>> =
 *   people.leftOuterJoin(animals, Person::favouriteSpecies, Animal::species)
 *
 * This is the most general left join, given a joining key it returns for each key a pair of relevant elements from the
 * left and right tables. It is "left outer" in the sense that all members of the left table are guaranteed to be in
 * the result, but this may not be the case for the right table.
 */
fun <A : Any, B : Any, K : Any> ObservableList<A>.leftOuterJoin(
        rightTable: ObservableList<B>,
        leftToJoinKey: (A) -> K,
        rightToJoinKey: (B) -> K
): ObservableMap<K, Pair<ObservableList<A>, ObservableList<B>>> {
    val leftTableMap = associateByAggregation(leftToJoinKey)
    val rightTableMap = rightTable.associateByAggregation(rightToJoinKey)
    return LeftOuterJoinedMap(leftTableMap, rightTableMap) { _, left, rightValue ->
        Pair(left, ChosenList(rightValue.map { it ?: FXCollections.emptyObservableList() }, "ChosenList from leftOuterJoin"))
    }
}

fun <A> ObservableList<A>.getValueAt(index: Int): ObservableValue<A?> {
    return Bindings.valueAt(this, index)
}

fun <A> ObservableList<A>.first(): ObservableValue<A?> {
    return getValueAt(0)
}

fun <A> ObservableList<A>.last(): ObservableValue<A?> {
    return Bindings.createObjectBinding({
        if (size > 0) {
            this[this.size - 1]
        } else {
            null
        }
    }, arrayOf(this))
}

fun <T : Any> ObservableList<T>.unique(): ObservableList<T> {
    return AggregatedList(this, { it }, { key, _ -> key })
}

fun <T : Any, K : Any> ObservableList<T>.distinctBy(toKey: (T) -> K): ObservableList<T> {
    return AggregatedList(this, toKey, { _, entryList -> entryList[0] })
}

fun ObservableValue<*>.isNotNull(): BooleanBinding {
    return Bindings.createBooleanBinding({ this.value != null }, arrayOf(this))
}

/**
 * Return first element of the observable list as observable value.
 * Return provided default value if the list is empty.
 */
fun <A> ObservableList<A>.firstOrDefault(default: ObservableValue<A?>, predicate: (A) -> Boolean): ObservableValue<A?> {
    return Bindings.createObjectBinding({ this.firstOrNull(predicate) ?: default.value }, arrayOf(this, default))
}

/**
 * Return first element of the observable list as observable value.
 * Return ObservableValue(null) if the list is empty.
 */
fun <A> ObservableList<A>.firstOrNullObservable(predicate: (A) -> Boolean): ObservableValue<A?> {
    return Bindings.createObjectBinding({ this.firstOrNull(predicate) }, arrayOf(this))
}

/**
 * Modifies the given Rx observable such that emissions are run on the JavaFX GUI thread. Use this when you have an Rx
 * observable that may emit in the background e.g. from the network and you wish to link it to the user interface.
 *
 * Note: you should use the returned observable, not the original one this method is called on.
 */
fun <T> Observable<T>.observeOnFXThread(): Observable<T> = observeOn(Schedulers.from(Platform::runLater))

/**
 * Given a [DataFeed] that contains the results of a vault query and a subsequent stream of changes, returns a JavaFX
 * [ObservableList] that mirrors the streamed results on the UI thread. Note that the paging is *not* respected by this
 * function: if a state is added that would not have appeared in the page in the initial query, it will still be added
 * to the observable list.
 *
 * @see toFXListOfStates if you want just the state objects and not the ledger pointers too.
 */
fun <T : ContractState> DataFeed<Vault.Page<T>, Vault.Update<T>>.toFXListOfStateRefs(): ObservableList<StateAndRef<T>> {
    val list = FXCollections.observableArrayList(snapshot.states)
    updates.observeOnFXThread().subscribe { (consumed, produced) ->
        list.removeAll(consumed)
        list.addAll(produced)
    }
    return list
}

/**
 * Returns the same list as [toFXListOfStateRefs] but which contains the states instead of [StateAndRef] wrappers.
 * The same notes apply as with that function.
 */
fun <T : ContractState> DataFeed<Vault.Page<T>, Vault.Update<T>>.toFXListOfStates(): ObservableList<T> {
    return toFXListOfStateRefs().map { it.state.data }
}
