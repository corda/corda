package net.corda.node.internal.cordapp

import com.typesafe.config.ConfigFactory
import net.corda.core.cordapp.CordappConfigException
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType

class TypesafeCordappConfigTests {
    @Test(timeout=300_000)
	fun `test that all value types can be retrieved`() {
        val config = ConfigFactory.parseString("string=string\nint=1\nfloat=1.0\ndouble=1.0\nnumber=2\ndouble=1.01\nbool=false")
        val cordappConf = TypesafeCordappConfig(config)

        assertThat(cordappConf.get("string")).isEqualTo("string")
        assertThat(cordappConf.getString("string")).isEqualTo("string")
        assertThat(cordappConf.getInt("int")).isEqualTo(1)
        assertThat(cordappConf.getFloat("float")).isEqualTo(1.0F)
        assertThat(cordappConf.getDouble("double")).isEqualTo(1.01)
        assertThat(cordappConf.getNumber("number")).isEqualTo(2)
        assertThat(cordappConf.getBoolean("bool")).isEqualTo(false)
    }

    @Test(timeout=300_000)
	fun `test a nested path`() {
        val config = ConfigFactory.parseString("outer: { inner: string }")
        val cordappConf = TypesafeCordappConfig(config)

        assertThat(cordappConf.getString("outer.inner")).isEqualTo("string")
    }

    @Test(timeout=300_000)
	fun `test exists determines existence and lack of existence correctly`() {
        val config = ConfigFactory.parseString("exists=exists")
        val cordappConf = TypesafeCordappConfig(config)

        assertThat(cordappConf.exists("exists")).isTrue()
        assertThat(cordappConf.exists("notexists")).isFalse()
    }

    @Test(timeout=300_000)
    fun `test that an exception is thrown when trying to access a non-extant field`() {
        val config = ConfigFactory.empty()
        val cordappConf = TypesafeCordappConfig(config)
        assertThatExceptionOfType(CordappConfigException::class.java).isThrownBy {
            cordappConf.get("anything")
        }
    }
}