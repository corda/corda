package net.corda.core.node

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class VersionTest {
    @Test
    fun `parse valid non-SNAPSHOT string`() {
        assertThat(Version.parse("1.2")).isEqualTo(Version(1, 2, null, false))
    }

    @Test
    fun `parse valid SNAPSHOT string`() {
        assertThat(Version.parse("2.23-SNAPSHOT")).isEqualTo(Version(2, 23, null, true))
    }

    @Test
    fun `parse string with just major number`() {
        assertThatThrownBy {
            Version.parse("2")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `parse string with unknown qualifier`() {
        assertThatThrownBy {
            Version.parse("2.3-TEST")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `parses patch version`() {
        assertThat(Version.parse("0.1.2")).isEqualTo(Version(0, 1, 2, false))
    }

    @Test
    fun `parses snapshot patch version`() {
        assertThat(Version.parse("0.1.2-SNAPSHOT")).isEqualTo(Version(0, 1, 2, true))
    }
}