package com.r3corda.client.testing

import co.paralleluniverse.strands.SettableFuture
import com.r3corda.core.ThreadBox
import org.reactfx.EventStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This file defines a simple DSL for testing non-deterministic sequence of events arriving on an [EventStream].
 *
 * [sequence] is used to impose ordering invariants on the stream, whereas [parallel] allows events to arrive in any order.
 *
 * The only restriction on [parallel] is that we should be able to discriminate which branch to take based on the
 * arrived event's type. If this is ambiguous the first matching piece of DSL will be run.

 * [sequence]s and [parallel]s can be nested arbitrarily
 *
 * Example usage:
 *
 * val stream: EventStream<SomeEvent> = (..)
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

val log: Logger = LoggerFactory.getLogger("Expect")

sealed class ExpectCompose<out E> {
    class Single<E>(val expect: Expect<E, E>) : ExpectCompose<E>()
    class Sequential<E>(val sequence: List<ExpectCompose<E>>) : ExpectCompose<E>()
    class Parallel<E>(val parallel: List<ExpectCompose<E>>) : ExpectCompose<E>()
}

data class Expect<E, T : E>(
        val clazz: Class<T>,
        val expectClosure: (T) -> Unit
)

inline fun <E : Any, reified T : E> expect(noinline expectClosure: (T) -> Unit): ExpectCompose<E> {
    return ExpectCompose.Single(Expect(T::class.java, expectClosure))
}

/**
 * Tests that events arrive in the specified order
 *
 * @param expects The pieces of DSL that should run sequentially when events arrive
 */
fun <E> sequence(vararg expects: ExpectCompose<E>) =
        ExpectCompose.Sequential(listOf(*expects))

/**
 * Tests that events arrive in unspecified order
 *
 * @param expects The pieces of DSL all of which should run but in an unspecified order depending on what sequence events arrive
 */
fun <E> parallel(vararg expects: ExpectCompose<E>) =
        ExpectCompose.Parallel(listOf(*expects))

sealed class ExpectComposeState<E : Any>{
    class Finished<E : Any> : ExpectComposeState<E>()
    class Single<E : Any>(val single: ExpectCompose.Single<E>) : ExpectComposeState<E>()
    class Sequential<E : Any>(
            val sequential: ExpectCompose.Sequential<E>,
            val index: Int,
            val state: ExpectComposeState<E>
    ) : ExpectComposeState<E>()
    class Parallel<E : Any>(
            val parallel:
            ExpectCompose.Parallel<E>,
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

fun <E : Any> EventStream<E>.expectEvents(expectCompose: ExpectCompose<E>) {
    val finishFuture = SettableFuture<Unit>()
    val lockedState = ThreadBox(object { var state = ExpectComposeState.fromExpectCompose(expectCompose) })
    subscribe { event ->
        lockedState.locked {
            if (state is ExpectComposeState.Finished) {
                log.warn("Got event $event, but was expecting no further events")
                return@subscribe
            }
            val next = state.nextState(event)
            log.info("$event :: ${state.getExpectedEvents()} -> ${next?.second?.getExpectedEvents()}")
            if (next == null) {
                val expectedStates = state.getExpectedEvents()
                finishFuture.setException(Exception(
                        "Got $event, expected one of $expectedStates"
                ))
                state = ExpectComposeState.Finished()
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


