package net.corda.example

import java.util.*
import kotlin.concurrent.schedule

class LambdaExpressions {
    private val timer: Timer = Timer()

    fun testing(block: Unit) {
        timer.schedule(Random().nextLong()) {
            block
        }
    }
}