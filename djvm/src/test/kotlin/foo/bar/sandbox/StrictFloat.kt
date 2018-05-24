package foo.bar.sandbox

import org.assertj.core.api.Assertions.assertThat

class StrictFloat : Callable {
    override fun call() {
        val d = java.lang.Double.MIN_VALUE
        val x = d / 2 * 2
        assertThat(x.toString()).isEqualTo("0.0")
    }
}

