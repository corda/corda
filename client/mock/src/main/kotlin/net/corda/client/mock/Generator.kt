package net.corda.client.mock

import net.corda.client.mock.Generator.Companion.choice
import net.corda.core.ErrorOr
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
 *   val animals = animalsGenerator.generate(SplittableRandom()).getOrThrow()
 *
 *   The above will generate a random list of animals.
 */
class Generator<out A>(val generate: (SplittableRandom) -> ErrorOr<A>) {

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
        fun <A> pure(value: A) = Generator { ErrorOr(value) }
        fun <A> impure(valueClosure: () -> A) = Generator { ErrorOr(valueClosure()) }
        fun <A> fail(error: Exception) = Generator<A> { ErrorOr.of(error) }

        // Alternative
        fun <A> choice(generators: List<Generator<A>>) = intRange(0, generators.size - 1).bind { generators[it] }

        fun <A> success(generate: (SplittableRandom) -> A) = Generator { ErrorOr(generate(it)) }
        fun <A> frequency(generators: List<Pair<Double, Generator<A>>>): Generator<A> {
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
                val v = element.value
                if (v != null) {
                    result.add(v)
                } else {
                    return@Generator ErrorOr.of(element.error!!)
                }
            }
            ErrorOr(result)
        }
    }
}

fun <A> Generator.Companion.frequency(vararg generators: Pair<Double, Generator<A>>) = frequency(generators.toList())

fun <A> Generator<A>.generateOrFail(random: SplittableRandom, numberOfTries: Int = 1): A {
    var error: Throwable? = null
    for (i in 0..numberOfTries - 1) {
        val result = generate(random)
        val v = result.value
        if (v != null) {
            return v
        } else {
            error = result.error
        }
    }
    if (error == null) {
        throw IllegalArgumentException("numberOfTries cannot be <= 0")
    } else {
        throw Exception("Failed to generate", error)
    }
}

fun Generator.Companion.int() = Generator.success(SplittableRandom::nextInt)
fun Generator.Companion.long() = Generator.success(SplittableRandom::nextLong)
fun Generator.Companion.bytes(size: Int): Generator<ByteArray> = Generator.success { random ->
    ByteArray(size) { random.nextInt().toByte() }
}

fun Generator.Companion.intRange(range: IntRange) = intRange(range.first, range.last)
fun Generator.Companion.intRange(from: Int, to: Int): Generator<Int> = Generator.success {
    (from + Math.abs(it.nextInt()) % (to - from + 1)).toInt()
}

fun Generator.Companion.longRange(range: LongRange) = longRange(range.first, range.last)
fun Generator.Companion.longRange(from: Long, to: Long): Generator<Long> = Generator.success {
    (from + Math.abs(it.nextLong()) % (to - from + 1)).toLong()
}

fun Generator.Companion.double() = Generator.success { it.nextDouble() }
fun Generator.Companion.doubleRange(from: Double, to: Double): Generator<Double> = Generator.success {
    from + it.nextDouble() * (to - from)
}

fun Generator.Companion.char() = Generator {
    val codePoint = Math.abs(it.nextInt()) % (17 * (1 shl 16))
    if (Character.isValidCodePoint(codePoint)) {
        return@Generator ErrorOr(codePoint.toChar())
    } else {
        ErrorOr.of(IllegalStateException("Could not generate valid codepoint"))
    }
}

fun Generator.Companion.string(meanSize: Double = 16.0) = replicatePoisson(meanSize, char()).map {
    val builder = StringBuilder()
    it.forEach {
        builder.append(it)
    }
    builder.toString()
}

fun <A> Generator.Companion.replicate(number: Int, generator: Generator<A>): Generator<List<A>> {
    val generators = mutableListOf<Generator<A>>()
    for (i in 1..number) {
        generators.add(generator)
    }
    return sequence(generators)
}


fun <A> Generator.Companion.replicatePoisson(meanSize: Double, generator: Generator<A>) = Generator<List<A>> {
    val chance = (meanSize - 1) / meanSize
    val result = mutableListOf<A>()
    var finish = false
    while (!finish) {
        val errorOr = Generator.doubleRange(0.0, 1.0).generate(it).bind { value ->
            if (value < chance) {
                generator.generate(it).map { result.add(it) }
            } else {
                finish = true
                ErrorOr(Unit)
            }
        }
        val e = errorOr.error
        if (e != null) {
            return@Generator ErrorOr.of(e)
        }
    }
    ErrorOr(result)
}

fun <A> Generator.Companion.pickOne(list: List<A>) = Generator.intRange(0, list.size - 1).map { list[it] }
fun <A> Generator.Companion.pickN(number: Int, list: List<A>) = Generator<List<A>> {
    val mask = BitSet(list.size)
    val size = Math.min(list.size, number)
    for (i in 0..size - 1) {
        // mask[i] = 1 desugars into mask.set(i, 1), which sets a range instead of a bit
        mask[i] = true
    }
    for (i in 0..list.size - 1) {
        val bit = mask[i]
        val swapIndex = i + it.nextInt(size - i)
        mask[i] = mask[swapIndex]
        mask[swapIndex] = bit
    }
    val resultList = ArrayList<A>()
    list.forEachIndexed { index, a ->
        if (mask[index]) {
            resultList.add(a)
        }
    }
    ErrorOr(resultList)
}

fun <A> Generator.Companion.sampleBernoulli(maxRatio: Double = 1.0, vararg collection: A) =
        sampleBernoulli(listOf(collection), maxRatio)

fun <A> Generator.Companion.sampleBernoulli(collection: Collection<A>, meanRatio: Double = 1.0): Generator<List<A>> =
        replicate(collection.size, Generator.doubleRange(0.0, 1.0)).map { chances ->
            val result = mutableListOf<A>()
            collection.forEachIndexed { index, element ->
                if (chances[index] < meanRatio) {
                    result.add(element)
                }
            }
            result
        }
