package com.r3corda.client.testing

import co.paralleluniverse.strands.SettableFuture
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

/**
 * This file defines a simple DSL for testing non-deterministic sequence of events arriving on an [Observable].
 *
 * [sequence] is used to impose ordering invariants on the stream, whereas [parallel] allows events to arrive in any order.
 *
 * The only restriction on [parallel] is that we should be able to discriminate which branch to take based on the
 * arrived event's type. If this is ambiguous the first matching piece of DSL will be run.

 * [sequence]s and [parallel]s can be nested arbitrarily
 *
 * Example usage:
 *
 * val stream: Ovservable<SomeEvent> = (..)
 * stream.expectEvents(
 *   sequence(
 *     expect { event: SomeEvent.A -> require(event.isOk()) },
 *     parallel(
 *       expect { event.SomeEvent.B -> },
 *       expect { event.SomeEvent.C -> }
 *     )
 *   )
 * )
 *
 * The above will test our expectation that the stream should first emit an A, and then a B and C in unspecified order.
 */

private val log: Logger = LoggerFactory.getLogger("Expect")

/**
 * Expect an event of type [T] and run [expectClosure] on it
 */
inline fun <E : Any, reified T : E> expect(noinline expectClosure: (T) -> Unit) = expect(T::class.java, expectClosure)
fun <E : Any, T : E> expect(klass: Class<T>, expectClosure: (T) -> Unit): ExpectCompose<E> {
    return ExpectCompose.Single(Expect(klass, expectClosure))
}

/**
 * Tests that events arrive in the specified order.
 *
 * @param expectations The pieces of DSL that should run sequentially when events arrive.
 */
fun <E> sequence(vararg expectations: ExpectCompose<E>): ExpectCompose<E> = ExpectCompose.Sequential(listOf(*expectations))

/**
 * Tests that events arrive in unspecified order.
 *
 * @param expectations The pieces of DSL all of which should run but in an unspecified order depending on what sequence events arrive.
 */
fun <E> parallel(vararg expectations: ExpectCompose<E>): ExpectCompose<E> = ExpectCompose.Parallel(listOf(*expectations))

/**
 * Tests that N events of the same type arrive
 *
 * @param number The number of events expected.
 * @param expectation The piece of DSL to run on each event, with the index of the event passed in.
 */
inline fun <E> repeat(number: Int, expectation: (Int) -> ExpectCompose<E>) = sequence(*Array(number) { expectation(it) })

/**
 * Run the specified DSL against the event stream.
 *
 * @param isStrict If false non-matched events are disregarded (so the DSL will only check a subset of events).
 * @param expectCompose The DSL we expect to match against the stream of events.
 */
fun <E : Any> Observable<E>.expectEvents(isStrict: Boolean = true, expectCompose: () -> ExpectCompose<E>) {
    val finishFuture = SettableFuture<Unit>()
    val stateLock = object {}
    var state = ExpectComposeState.fromExpectCompose(expectCompose())
    subscribe { event ->
        synchronized(stateLock) {
            if (state is ExpectComposeState.Finished) {
                log.warn("Got event $event, but was expecting no further events")
                return@subscribe
            }
            val next = state.nextState(event)
            log.info("$event :: ${state.getExpectedEvents()} -> ${next?.second?.getExpectedEvents()}")
            if (next == null) {
                val expectedStates = state.getExpectedEvents()
                val message = "Got $event, expected one of $expectedStates"
                if (isStrict) {
                    finishFuture.setException(Exception(message))
                    state = ExpectComposeState.Finished()
                } else {
                    log.warn("$message, discarding event as isStrict=false")
                }
            } else {
                state = next.second
                try {
                    next.first()
                } catch (exception: Exception) {
                    finishFuture.setException(exception)
                }
                if (state is ExpectComposeState.Finished) {
                    finishFuture.set(Unit)
                }
            }
        }
    }
    finishFuture.get()
}

sealed class ExpectCompose<out E> {
    internal class Single<E>(val expect: Expect<E, E>) : ExpectCompose<E>()
    internal class Sequential<E>(val sequence: List<ExpectCompose<E>>) : ExpectCompose<E>()
    internal class Parallel<E>(val parallel: List<ExpectCompose<E>>) : ExpectCompose<E>()
}

internal data class Expect<E, T : E>(
        val clazz: Class<T>,
        val expectClosure: (T) -> Unit
)

private sealed class ExpectComposeState<E : Any> {
    class Finished<E : Any> : ExpectComposeState<E>()
    class Single<E : Any>(val single: ExpectCompose.Single<E>) : ExpectComposeState<E>()
    class Sequential<E : Any>(
            val sequential: ExpectCompose.Sequential<E>,
            val index: Int,
            val state: ExpectComposeState<E>
    ) : ExpectComposeState<E>()
    class Parallel<E : Any>(
            val parallel: ExpectCompose.Parallel<E>,
            val states: List<ExpectComposeState<E>>
    ) : ExpectComposeState<E>()

    companion object {
        fun <E : Any> fromExpectCompose(expectCompose: ExpectCompose<E>): ExpectComposeState<E> {
            return when (expectCompose) {
                is ExpectCompose.Single -> Single(expectCompose)
                is ExpectCompose.Sequential -> {
                    if (expectCompose.sequence.size > 0) {
                        Sequential(expectCompose, 0, fromExpectCompose(expectCompose.sequence[0]))
                    } else {
                        Finished()
                    }
                }
                is ExpectCompose.Parallel -> {
                    if (expectCompose.parallel.size > 0) {
                        Parallel(expectCompose, expectCompose.parallel.map { fromExpectCompose(it) })
                    } else {
                        Finished()
                    }
                }
            }
        }
    }

    fun getExpectedEvents(): List<Class<out E>> {
        return when (this) {
            is ExpectComposeState.Finished -> listOf()
            is ExpectComposeState.Single -> listOf(single.expect.clazz)
            is ExpectComposeState.Sequential -> state.getExpectedEvents()
            is ExpectComposeState.Parallel -> states.flatMap { it.getExpectedEvents() }
        }
    }

    fun nextState(event: E): Pair<() -> Unit, ExpectComposeState<E>>? {
        return when (this) {
            is ExpectComposeState.Finished -> null
            is ExpectComposeState.Single -> {
                if (single.expect.clazz.isAssignableFrom(event.javaClass)) {
                    @Suppress("UNCHECKED_CAST")
                    Pair({ single.expect.expectClosure(event) }, Finished())
                } else {
                    null
                }
            }
            is ExpectComposeState.Sequential -> {
                val next = state.nextState(event)
                if (next == null) {
                    null
                } else if (next.second is Finished) {
                    if (index == sequential.sequence.size - 1) {
                        Pair(next.first, Finished<E>())
                    } else {
                        val nextState = fromExpectCompose(sequential.sequence[index + 1])
                        if (nextState is Finished) {
                            Pair(next.first, Finished<E>())
                        } else {
                            Pair(next.first, Sequential(sequential, index + 1, nextState))
                        }
                    }
                } else {
                    Pair(next.first, Sequential(sequential, index, next.second))
                }
            }
            is ExpectComposeState.Parallel -> {
                states.forEachIndexed { stateIndex, state ->
                    val next = state.nextState(event)
                    if (next != null) {
                        val nextStates = states.mapIndexed { i, expectComposeState ->
                            if (i == stateIndex) next.second else expectComposeState
                        }
                        if (nextStates.all { it is Finished }) {
                            return Pair(next.first, Finished())
                        } else {
                            return Pair(next.first, Parallel(parallel, nextStates))
                        }
                    }
                }
                null
            }
        }
    }
}
