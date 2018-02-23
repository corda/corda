package net.corda.behave.monitoring

import net.corda.behave.second
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.Observable

class MonitoringTests {

    @Test
    fun `watch gets triggered when pattern is observed`() {
        val observable = Observable.just("first", "second", "third")
        val result = PatternWatch("c.n").await(observable, 1.second)
        assertThat(result).isTrue()
    }

    @Test
    fun `watch does not get triggered when pattern is not observed`() {
        val observable = Observable.just("first", "second", "third")
        val result = PatternWatch("forth").await(observable, 1.second)
        assertThat(result).isFalse()
    }

}
