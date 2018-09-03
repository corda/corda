package net.corda.behave.monitoring

import net.corda.core.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.Observable

class MonitoringTests {

    @Test
    fun `watch gets triggered when pattern is observed`() {
        val observable = Observable.just("first", "second", "third")
        val result = PatternWatch(observable, "c.n").await(1.seconds)
        assertThat(result).isTrue()
    }

    @Test
    fun `watch does not get triggered when pattern is not observed`() {
        val observable = Observable.just("first", "second", "third")
        val result = PatternWatch(observable, "forth").await(1.seconds)
        assertThat(result).isFalse()
    }

    @Test
    fun `conjunctive watch gets triggered when all its constituents match on the input`() {
        val observable = Observable.just("first", "second", "third")
        val watch1 = PatternWatch(observable, "fir")
        val watch2 = PatternWatch(observable, "ond")
        val watch3 = PatternWatch(observable, "ird")
        val aggregate = watch1 * watch2 * watch3
        assertThat(aggregate.await(1.seconds)).isTrue()
    }

    @Test
    fun `conjunctive watch does not get triggered when one or more of its constituents do not match on the input`() {
        val observable = Observable.just("first", "second", "third")
        val watch1 = PatternWatch(observable, "fir")
        val watch2 = PatternWatch(observable, "ond")
        val watch3 = PatternWatch(observable, "baz")
        val aggregate = watch1 * watch2 * watch3
        assertThat(aggregate.await(1.seconds)).isFalse()
    }

    @Test
    fun `disjunctive watch gets triggered when one or more of its constituents match on the input`() {
        val observable = Observable.just("first", "second", "third")
        val watch1 = PatternWatch(observable, "foo")
        val watch2 = PatternWatch(observable, "ond")
        val watch3 = PatternWatch(observable, "bar")
        val aggregate = watch1 / watch2 / watch3
        assertThat(aggregate.await(1.seconds)).isTrue()
    }

    @Test
    fun `disjunctive watch does not get triggered when none its constituents match on the input`() {
        val observable = Observable.just("first", "second", "third")
        val watch1 = PatternWatch(observable, "foo")
        val watch2 = PatternWatch(observable, "baz")
        val watch3 = PatternWatch(observable, "bar")
        val aggregate = watch1 / watch2 / watch3
        assertThat(aggregate.await(1.seconds)).isFalse()
    }
}