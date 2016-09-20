package com.r3corda.client.fxutils

import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import org.fxmisc.easybind.EasyBind
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
 */
fun <A, B> ObservableList<out A>.map(function: (A) -> B): ObservableList<B> = EasyBind.map(this, function)

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
fun <A, B> ObservableValue<out A>.bind(function: (A) -> ObservableValue<out B>): ObservableValue<out B> =
        // We cast here to enforce variance, flatMap should be covariant
        @Suppress("UNCHECKED_CAST")
        EasyBind.monadic(this).flatMap(function as (A) -> ObservableValue<B>)

/**
 * enum class FilterCriterion { HEIGHT, NAME }
 * val filterCriterion: ObservableValue<FilterCriterion> = (..)
 * val people: ObservableList<Person> = (..)
 * fun filterFunction(filterCriterion: FilterCriterion): (Person) -> Boolean { .. }
 *
 * val filteredPeople: ObservableList<Person> = people.filter(filterCriterion.map(filterFunction))
 */
fun <A> ObservableList<out A>.filter(predicate: ObservableValue<out (A) -> Boolean>): ObservableList<out A> {
    // We cast here to enforce variance, FilteredList should be covariant
    @Suppress("UNCHECKED_CAST")
    return FilteredList<A>(this as ObservableList<A>).apply {
        predicateProperty().bind(predicate.map { predicateFunction ->
            Predicate<A> { predicateFunction(it) }
        })
    }
}

/**
 * val people: ObservableList<Person> = (..)
 * val concatenatedNames = people.fold("", { names, person -> names + person.name })
 * val concatenatedNames2 = people.map(Person::name).fold("", String::plus)
 */
fun <A, B> ObservableList<out A>.fold(initial: B, folderFunction: (B, A) -> B): ObservableValue<B> {
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
fun <A> ObservableList<out ObservableValue<out A>>.flatten(): ObservableList<out A> = FlattenedList(this)
