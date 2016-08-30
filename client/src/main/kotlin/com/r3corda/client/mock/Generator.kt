package com.r3corda.client.mock

import java.util.*

/**
 * This file defines a basic [Generator] library for composing random generators of objects.
 *
 * An object of type [Generator]<[A]> captures a generator of [A]s. Generators may be composed in several ways.
 *
 * [Generator.choice] picks a generator from the specified list and runs that.
 * [Generator.frequency] is similar to [choice] but the probability may be specified for each generator (it is normalised before picking).
 * [Generator.combine] combines two generators of A and B with a function (A, B) -> C. Variants exist for other arities.
 * [Generator.bind] sequences two generators using an arbitrary A->Generator<B> function. Keep the usage of this
 *   function minimal as it may explode the stack, especially when using recursion.
 *
 * There are other utilities as well, the type of which are usually descriptive.
 *
 * Example:
 *   val birdNameGenerator = Generator.pickOne(listOf("raven", "pigeon"))
 *   val birdHeightGenerator = Generator.doubleRange(from = 10.0, to = 30.0)
 *   val birdGenerator = birdNameGenerator.combine(birdHeightGenerator) { name, height -> Bird(name, height) }
 *   val birdsGenerator = Generator.replicate(2, birdGenerator)
 *   val mammalsGenerator = Generator.sampleBernoulli(listOf(Mammal("fox"), Mammal("elephant")))
 *   val animalsGenerator = Generator.frequency(
 *     0.2 to birdsGenerator,
 *     0.8 to mammalsGenerator
 *   )
 *   val animals = animalsGenerator.generate(Random()).getOrThrow()
 *
 *   The above will generate a random list of animals.
 */
class Generator<out A>(val generate: (Random) -> ErrorOr<A>) {

    // Functor
    fun <B> map(function: (A) -> B): Generator<B> =
            Generator { generate(it).map(function) }

    // Applicative
    fun <B> product(other: Generator<(A) -> B>) =
            Generator { generate(it).combine(other.generate(it)) { a, f -> f(a) } }
    fun <B, R> combine(other1: Generator<B>, function: (A, B) -> R) =
            product<R>(other1.product(pure({ b -> { a -> function(a, b) } })))
    fun <B, C, R> combine(other1: Generator<B>, other2: Generator<C>, function: (A, B, C) -> R) =
            product<R>(other1.product(other2.product(pure({ c -> { b -> { a -> function(a, b, c) } } }))))
    fun <B, C, D, R> combine(other1: Generator<B>, other2: Generator<C>, other3: Generator<D>, function: (A, B, C, D) -> R) =
            product<R>(other1.product(other2.product(other3.product(pure({ d -> { c -> { b -> { a -> function(a, b, c, d) } } } })))))
    fun <B, C, D, E, R> combine(other1: Generator<B>, other2: Generator<C>, other3: Generator<D>, other4: Generator<E>, function: (A, B, C, D, E) -> R) =
            product<R>(other1.product(other2.product(other3.product(other4.product(pure({ e -> { d -> { c -> { b -> { a -> function(a, b, c, d, e) } } } } }))))))

    // Monad
    fun <B> bind(function: (A) -> Generator<B>) =
            Generator { generate(it).bind { a -> function(a).generate(it) } }

    companion object {
        fun <A> pure(value: A) = Generator { ErrorOr.Success(value) }
        fun <A> impure(valueClosure: () -> A) = Generator { ErrorOr.Success(valueClosure()) }
        fun <A> fail(error: String) = Generator<A> { ErrorOr.Error(error) }

        // Alternative
        fun <A> choice(generators: List<Generator<A>>) = intRange(0, generators.size - 1).bind { generators[it] }

        fun <A> success(generate: (Random) -> A) = Generator { ErrorOr.Success(generate(it)) }
        fun <A> frequency(vararg generators: Pair<Double, Generator<A>>): Generator<A> {
            val ranges = mutableListOf<Pair<Double, Double>>()
            var current = 0.0
            generators.forEach {
                val next = current + it.first
                ranges.add(Pair(current, next))
                current = next
            }
            return doubleRange(0.0, current).bind { value ->
                generators[ranges.binarySearch { range ->
                    if (value < range.first) {
                        1
                    } else if (value < range.second) {
                        0
                    } else {
                        -1
                    }
                }].second
            }
        }

        fun <A> sequence(generators: List<Generator<A>>) = Generator<List<A>> {
            val result = mutableListOf<A>()
            for (generator in generators) {
                val element = generator.generate(it)
                when (element) {
                    is ErrorOr.Error -> return@Generator ErrorOr.Error(element.error)
                    is ErrorOr.Success -> result.add(element.value)
                }
            }
            ErrorOr.Success(result)
        }
    }
}

fun <A> Generator.Companion.oneOf(list: List<A>) = intRange(0, list.size - 1).map { list[it] }

fun <A> Generator<A>.generateOrFail(random: Random, numberOfTries: Int = 1): A {
    var error: String? = null
    for (i in 0 .. numberOfTries - 1) {
        val result = generate(random)
        when (result) {
            is ErrorOr.Error -> error = result.error
            is ErrorOr.Success -> return result.value
        }
    }
    if (error == null) {
        throw IllegalArgumentException("numberOfTries cannot be <= 0")
    } else {
        throw Exception("Failed to generate, last error $error")
    }
}

fun Generator.Companion.int() = Generator.success { it.nextInt() }
fun Generator.Companion.intRange(from: Int, to: Int): Generator<Int> = Generator.success {
    (from + Math.abs(it.nextInt()) % (to - from + 1)).toInt()
}
fun Generator.Companion.double() = Generator.success { it.nextDouble() }
fun Generator.Companion.doubleRange(from: Double, to: Double): Generator<Double> = Generator.success {
    from + it.nextDouble() % (to - from)
}

fun <A> Generator.Companion.replicate(number: Int, generator: Generator<A>): Generator<List<A>> {
    val generators = mutableListOf<Generator<A>>()
    for (i in 1 .. number) {
        generators.add(generator)
    }
    return sequence(generators)
}


fun <A> Generator.Companion.replicatePoisson(meanSize: Double, generator: Generator<A>) = Generator<List<A>> {
    val chance = (meanSize - 1) / meanSize
    val result = mutableListOf<A>()
    var finish = false
    while (!finish) {
        val error = Generator.doubleRange(0.0, 1.0).generate(it).bind { value ->
            if (value < chance) {
                generator.generate(it).map { result.add(it) }
            } else {
                finish = true
                ErrorOr.Success(Unit)
            }
        }
        if (error is ErrorOr.Error) {
            return@Generator ErrorOr.Error(error.error)
        }
    }
    ErrorOr.Success(result)
}

fun <A> Generator.Companion.pickOne(list: List<A>) = Generator.intRange(0, list.size - 1).map { list[it] }

fun <A> Generator.Companion.sampleBernoulli(collection: Collection<A>, maxRatio: Double = 1.0): Generator<List<A>> =
        intRange(0, (maxRatio * collection.size).toInt()).bind { howMany ->
            replicate(collection.size, Generator.doubleRange(0.0, 1.0)).map { chances ->
                val result = mutableListOf<A>()
                collection.forEachIndexed { index, element ->
                    if (chances[index] < howMany.toDouble() / collection.size.toDouble()) {
                        result.add(element)
                    }
                }
                result
            }
        }
