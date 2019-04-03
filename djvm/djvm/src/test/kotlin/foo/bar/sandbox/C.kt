package foo.bar.sandbox

import org.assertj.core.api.Assertions.assertThat

class C : Callable {
    override fun call() {
        val obj = MyObject()
        assertThat(obj.hashCode()).isEqualTo(0)
    }
}

