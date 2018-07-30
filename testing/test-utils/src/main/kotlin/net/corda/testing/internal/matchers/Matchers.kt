package net.corda.testing.internal.matchers

import com.natpryce.hamkrest.*

internal fun indent(description: String) = description.lineSequence().map { "\t$it" }.joinToString("\n")

fun hasEntrySetSize(expected: Int) = object : Matcher<Map<*, *>> {
    override val description = "is a map of size $expected"
    override fun invoke(actual: Map<*, *>) =
        if (actual.size == expected) {
            MatchResult.Match
        } else {
            MatchResult.Mismatch("was a map of size ${actual.size}")
        }
}

fun <T> Matcher<T>.redescribe(redescriber: (String) -> String) = object : Matcher<T> {
    override val description = redescriber(this@redescribe.description)
    override fun invoke(actual: T) = this@redescribe(actual)
}

fun <T> Matcher<T>.redescribeMismatch(redescriber: (String) -> String) = object : Matcher<T> {
    override val description = this@redescribeMismatch.description
    override fun invoke(actual: T) = this@redescribeMismatch(actual).modifyMismatchDescription(redescriber)
}

fun MatchResult.modifyMismatchDescription(modify: (String) -> String) = when(this) {
    is MatchResult.Match -> MatchResult.Match
    is MatchResult.Mismatch -> MatchResult.Mismatch(modify(this.description))
}

fun <O, I> Matcher<I>.extrude(projection: (O) -> I) = object : Matcher<O> {
    override val description = this@extrude.description
    override fun invoke(actual: O) = this@extrude(projection(actual))
}

internal fun <K, V> hasAnEntry(key: K, valueMatcher: Matcher<V>) = object : Matcher<Map<K, V>> {
    override val description = "$key: ${valueMatcher.description}"
    override fun invoke(actual: Map<K, V>): MatchResult =
            actual[key]?.let { valueMatcher(it) }?.let { when(it) {
                is MatchResult.Match -> it
                is MatchResult.Mismatch -> MatchResult.Mismatch("$key: ${it.description}")
            }} ?: MatchResult.Mismatch("$key was not present")
}

fun <K, V> hasEntry(key: K, valueMatcher: Matcher<V>) =
        hasAnEntry(key, valueMatcher).redescribe { "Is a map containing the entry:\n${indent(it)}"}

fun <K, V> hasOnlyEntries(vararg entryMatchers: Pair<K, Matcher<V>>) = hasOnlyEntries(entryMatchers.toList())

fun <K, V> hasOnlyEntries(entryMatchers: Collection<Pair<K, Matcher<V>>>) =
        hasEntrySetSize(entryMatchers.size) and hasEntries(entryMatchers)

fun <K, V> hasEntries(vararg entryMatchers: Pair<K, Matcher<V>>) = hasEntries(entryMatchers.toList())

fun <K, V> hasEntries(entryMatchers: Collection<Pair<K, Matcher<V>>>) = object : Matcher<Map<K, V>> {
    override val description =
            "is a map containing the entries:\n" +
                    entryMatchers.asSequence()
                        .joinToString("\n") { indent("${it.first}: ${it.second.description}") }

    override fun invoke(actual: Map<K, V>): MatchResult {
        val mismatches = entryMatchers.map { hasAnEntry(it.first, it.second)(actual) }
                .filterIsInstance<MatchResult.Mismatch>()

        return if (mismatches.isEmpty()) {
            MatchResult.Match
        } else {
            MatchResult.Mismatch(
                    "had entries which did not meet criteria:\n" +
                            mismatches.joinToString("\n") { indent(it.description) })
        }
    }
}

fun <T> allOf(vararg matchers: Matcher<T>) = allOf(matchers.toList())

fun <T> allOf(matchers: Collection<Matcher<T>>) = object : Matcher<T> {
    override val description =
            "meets all of the criteria:\n" +
                    matchers.asSequence()
                            .joinToString("\n") { indent(it.description) }

    override fun invoke(actual: T) : MatchResult {
        val mismatches = matchers.map { it(actual) }
                .filterIsInstance<MatchResult.Mismatch>()

        return if (mismatches.isEmpty()) {
            MatchResult.Match
        } else {
            MatchResult.Mismatch(
                    "did not meet criteria:\n" +
                            mismatches.joinToString("\n") { indent(it.description) })
        }
    }
}