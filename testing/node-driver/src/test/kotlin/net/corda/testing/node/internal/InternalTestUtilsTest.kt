package net.corda.testing.node.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class InternalTestUtilsTest {
    @Test
    fun `test simplifyScanPackages`() {
        assertThat(simplifyScanPackages(emptyList())).isEmpty()
        assertThat(simplifyScanPackages(listOf("com.foo.bar"))).containsExactlyInAnyOrder("com.foo.bar")
        assertThat(simplifyScanPackages(listOf("com.foo", "com.foo"))).containsExactlyInAnyOrder("com.foo")
        assertThat(simplifyScanPackages(listOf("com.foo", "com.bar"))).containsExactlyInAnyOrder("com.foo", "com.bar")
        assertThat(simplifyScanPackages(listOf("com.foo", "com.foo.bar"))).containsExactlyInAnyOrder("com.foo")
        assertThat(simplifyScanPackages(listOf("com.foo.bar", "com.foo"))).containsExactlyInAnyOrder("com.foo")
        assertThat(simplifyScanPackages(listOf("com.foobar", "com.foo.bar"))).containsExactlyInAnyOrder("com.foobar", "com.foo.bar")
        assertThat(simplifyScanPackages(listOf("com.foobar", "com.foo"))).containsExactlyInAnyOrder("com.foobar", "com.foo")
    }
}
