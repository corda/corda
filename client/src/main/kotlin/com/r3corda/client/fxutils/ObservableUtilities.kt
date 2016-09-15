package com.r3corda.client.fxutils

import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.StateAndRef
import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import kotlinx.support.jdk8.collections.stream
import org.fxmisc.easybind.EasyBind
import java.util.function.Predicate
import java.util.stream.Collector

fun <A, B> ObservableValue<out A>.map(function: (A) -> B): ObservableValue<B> = EasyBind.map(this, function)
fun <A, B> ObservableList<out A>.map(function: (A) -> B): ObservableList<B> = EasyBind.map(this, function)

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

fun <A, B> ObservableValue<out A>.bind(function: (A) -> ObservableValue<out B>): ObservableValue<out B> =
        // We cast here to enforce variance, flatMap should be covariant
        @Suppress("UNCHECKED_CAST")
        EasyBind.monadic(this).flatMap(function as (A) -> ObservableValue<B>)

fun <A> ObservableList<out A>.filter(predicate: ObservableValue<out (A) -> Boolean>): ObservableList<out A> {
    // We cast here to enforce variance, FilteredList should be covariant
    @Suppress("UNCHECKED_CAST")
    return FilteredList<A>(this as ObservableList<A>).apply {
        predicateProperty().bind(predicate.map { predicateFunction ->
            Predicate<A> { predicateFunction(it) }
        })
    }
}

fun <A, B> ObservableList<out A>.fold(initial: B, folderFunction: (B, A) -> B): ObservableValue<B> {
    return Bindings.createObjectBinding({
        var current = initial
        forEach {
            current = folderFunction(current, it)
        }
        current
    }, arrayOf(this))
}

fun <A> ObservableList<out ObservableValue<out A>>.flatten(): ObservableList<out A> {
    return FlattenedList(this)
}

fun sum(a: Int, b: Int): Int = a + b

fun main(args: Array<String>) {
    val a = SimpleObjectProperty(0)
    val b = SimpleObjectProperty(0)

    ::sum.lift(a, b).addListener { observableValue, i0, i1 ->
        println(i1)
    }

    a.set(2)
    b.set(4)
}
