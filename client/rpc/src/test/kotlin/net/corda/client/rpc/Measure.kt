package net.corda.client.rpc

import net.corda.core.internal.uncheckedCast
import kotlin.reflect.KCallable
import kotlin.reflect.jvm.reflect

/**
 * These functions may be used to run measurements of a function where the parameters are chosen from corresponding
 * [Iterable]s in a lexical manner. An example use case would be benchmarking the speed of a certain function call using
 * different combinations of parameters.
 */

fun <A : Any, R> measure(a: Iterable<A>, f: (A) -> R) =
        measure(listOf(a), f.reflect()!!) { f(uncheckedCast(it[0])) }

fun <A : Any, B : Any, R> measure(a: Iterable<A>, b: Iterable<B>, f: (A, B) -> R) =
        measure(listOf(a, b), f.reflect()!!) { f(uncheckedCast(it[0]), uncheckedCast(it[1])) }

fun <A : Any, B : Any, C : Any, R> measure(a: Iterable<A>, b: Iterable<B>, c: Iterable<C>, f: (A, B, C) -> R) =
        measure(listOf(a, b, c), f.reflect()!!) { f(uncheckedCast(it[0]), uncheckedCast(it[1]), uncheckedCast(it[2])) }

fun <A : Any, B : Any, C : Any, D : Any, R> measure(a: Iterable<A>, b: Iterable<B>, c: Iterable<C>, d: Iterable<D>, f: (A, B, C, D) -> R) =
        measure(listOf(a, b, c, d), f.reflect()!!) { f(uncheckedCast(it[0]), uncheckedCast(it[1]), uncheckedCast(it[2]), uncheckedCast(it[3])) }

private fun <R> measure(paramIterables: List<Iterable<Any?>>, kCallable: KCallable<R>, call: (Array<Any?>) -> R): Iterable<MeasureResult<R>> {
    val kParameters = kCallable.parameters
    return iterateLexical(paramIterables).map { params ->
        MeasureResult(
                // For example an underscore param in a lambda does not have a name:
                parameters = params.mapIndexed { index, param -> Pair(kParameters[index].name, param) },
                result = call(params.toTypedArray())
        )
    }
}

data class MeasureResult<out R>(
        val parameters: List<Pair<String?, Any?>>,
        val result: R
)

fun <A> iterateLexical(iterables: List<Iterable<A>>): Iterable<List<A>> {
    val result = ArrayList<List<A>>()
    fun iterateLexicalHelper(index: Int, list: List<A>) {
        if (index < iterables.size) {
            iterables[index].forEach {
                iterateLexicalHelper(index + 1, list + it)
            }
        } else {
            result.add(list)
        }
    }
    iterateLexicalHelper(0, emptyList())
    return result
}
