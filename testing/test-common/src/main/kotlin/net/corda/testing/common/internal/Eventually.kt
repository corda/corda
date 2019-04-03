package net.corda.testing.common.internal

import java.time.Duration

/**
 * Ideas borrowed from "io.kotlintest" with some improvements made
 * This is meant for use from Kotlin code use only mainly due to it's inline/reified nature
 *
 * @param duration How long to wait for, before returning the last test failure. The default is 5 seconds.
 * @param waitBetween How long to wait before retrying the test condition. The default is 1/10th of a second.
 * @param waitBefore How long to wait before trying the test condition for the first time. It's assumed that [eventually]
 * is being used because the condition is not _immediately_ fulfilled, so this defaults to the value of [waitBetween].
 * @param test A test which should pass within the given [duration].
 *
 * @throws AssertionError, if the test does not pass within the given [duration].
 */
inline fun <R> eventually(
        duration: Duration = Duration.ofSeconds(5),
        waitBetween: Duration = Duration.ofMillis(100),
        waitBefore: Duration = waitBetween,
        test: () -> R): R {
    val end = System.nanoTime() + duration.toNanos()
    var times = 0
    var lastFailure: AssertionError? = null

    if (!waitBefore.isZero) Thread.sleep(waitBefore.toMillis())

    while (System.nanoTime() < end) {
        try {
            return test()
        } catch (e: AssertionError) {
            if (!waitBetween.isZero) Thread.sleep(waitBetween.toMillis())
            lastFailure = e
        }
        times++
    }

    throw AssertionError("Test failed with \"${lastFailure?.message}\" after $duration; attempted $times times")
}


/**
 * Use when the action you want to retry until it succeeds throws an exception, rather than failing a test.
 */
inline fun <R> succeeds(action: () -> R): R =
    try {
        action()
    } catch (e: Exception) {
        throw AssertionError(e.message)
    }