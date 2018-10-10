package foo.bar.sandbox

import java.util.*

fun testRandom(): Int {
    val random = Random()
    return random.nextInt()
}

fun String.toNumber(): Int {
    return this.toInt()
}
