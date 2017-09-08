package net.corda.testing

import kotlin.reflect.KCallable
import kotlin.reflect.jvm.reflect

/**
 * These functions may be used to run measurements of a function where the parameters are chosen from corresponding
 * [Iterable]s in a lexical manner. An example use case would be benchmarking the speed of a certain function call using
 * different combinations of parameters.
 */

@Suppress("UNCHECKED_CAST")
fun <A, R> measure(a: Iterable<A>, f: (A) -> R) =
        measure(listOf(a), f.reflect()!!) { (f as ((Any?)->R))(it[0]) }
@Suppress("UNCHECKED_CAST")
fun <A, B, R> measure(a: Iterable<A>, b: Iterable<B>, f: (A, B) -> R) =
        measure(listOf(a, b), f.reflect()!!) { (f as ((Any?,Any?)->R))(it[0], it[1]) }
@Suppress("UNCHECKED_CAST")
fun <A, B, C, R> measure(a: Iterable<A>, b: Iterable<B>, c: Iterable<C>, f: (A, B, C) -> R) =
        measure(listOf(a, b, c), f.reflect()!!) { (f as ((Any?,Any?,Any?)->R))(it[0], it[1], it[2]) }
@Suppress("UNCHECKED_CAST")
fun <A, B, C, D, R> measure(a: Iterable<A>, b: Iterable<B>, c: Iterable<C>, d: Iterable<D>, f: (A, B, C, D) -> R) =
        measure(listOf(a, b, c, d), f.reflect()!!) { (f as ((Any?,Any?,Any?,Any?)->R))(it[0], it[1], it[2], it[3]) }

private fun <R> measure(paramIterables: List<Iterable<Any?>>, kCallable: KCallable<R>, call: (Array<Any?>) -> R): Iterable<MeasureResult<R>> {
    val kParameters = kCallable.parameters
    return iterateLexical(paramIterables).map { params ->
        MeasureResult(
                parameters = params.mapIndexed { index, param -> Pair(kParameters[index].name!!, param) },
                result = call(params.toTypedArray())
        )
    }
}

data class MeasureResult<out R>(
    val parameters: List<Pair<String, Any?>>,
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
