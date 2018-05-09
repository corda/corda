package net.corda.vega.analytics

fun compareIMTriples(a: InitialMarginTriple, b: InitialMarginTriple): Boolean {
    return withinTolerance(a.first, b.first) && withinTolerance(a.second, b.second) && withinTolerance(a.third, b.third)
}

// TODO: Do this correctly
private fun <A, B> withinTolerance(first: A, second: B): Boolean {
    if (first is Double && second is Double) {
        val q = Math.abs(first - second)
        if (q < 0.001) return true
    }
    return false
}
