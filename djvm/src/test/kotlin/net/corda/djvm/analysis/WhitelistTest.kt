package net.corda.djvm.analysis

import net.corda.djvm.TestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class WhitelistTest : TestBase() {

    @Test
    fun `can determine when a class is whitelisted when namespace is covered`() {
        val whitelist = Whitelist.DEFAULT
        assertThat(whitelist.matches("java/lang/Object")).isTrue()
        assertThat(whitelist.matches("java/lang/Object.<init>:()V")).isTrue()
        assertThat(whitelist.matches("java/lang/Integer")).isTrue()
        assertThat(whitelist.matches("java/lang/Integer.<init>:(I)V")).isTrue()
    }

    @Test
    fun `can determine when a class is not whitelisted when namespace is covered`() {
        val whitelist = Whitelist.DEFAULT
        assertThat(whitelist.matches("java/util/Random")).isFalse()
        assertThat(whitelist.matches("java/util/Random.<init>:()V")).isFalse()
        assertThat(whitelist.matches("java/util/Random.nextInt:()I")).isFalse()
    }

    @Test
    fun `can determine when a class is whitelisted when namespace is not covered`() {
        val whitelist = TEST_WHITELIST
        assertThat(whitelist.matches("org/junit/Test")).isTrue()
        assertThat(whitelist.matches("org/assertj/core/api/Assertions")).isTrue()
        assertThat(whitelist.matches("net/foo/bar/Baz")).isFalse()
    }

    @Test
    fun `can determine when a namespace is not covered`() {
        val whitelist = Whitelist.DEFAULT
        assertThat(whitelist.matches("java/lang/Object")).isTrue()
        assertThat(whitelist.matches("org/junit/Test")).isFalse()
    }

}
