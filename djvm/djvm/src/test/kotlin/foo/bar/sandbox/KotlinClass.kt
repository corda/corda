package foo.bar.sandbox

fun testClock(): Long {
    return System.nanoTime()
}

fun String.toNumber(): Long {
    return this.toLong()
}
