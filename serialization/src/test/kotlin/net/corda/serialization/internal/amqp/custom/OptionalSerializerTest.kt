package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.SerializerFactory
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.assertThat
import org.junit.Test
import org.mockito.Mockito
import java.util.*

class OptionalSerializerTest {
    @Test(timeout=300_000)
	fun `should convert optional with item to proxy`() {
        val opt = Optional.of("GenericTestString")
        val proxy = OptionalSerializer(Mockito.mock(SerializerFactory::class.java)).toProxy(opt)
        assertThat(proxy.item, `is`<Any>("GenericTestString"))
    }

    @Test(timeout=300_000)
	fun `should convert optional without item to empty proxy`() {
        val opt = Optional.ofNullable<String>(null)
        val proxy = OptionalSerializer(Mockito.mock(SerializerFactory::class.java)).toProxy(opt)
        assertThat(proxy.item, `is`(nullValue()))
    }

    @Test(timeout=300_000)
	fun `should convert proxy without item to empty optional `() {
        val proxy = OptionalSerializer.OptionalProxy(null)
        val opt = OptionalSerializer(Mockito.mock(SerializerFactory::class.java)).fromProxy(proxy)
        assertThat(opt.isPresent, `is`(false))
    }

    @Test(timeout=300_000)
	fun `should convert proxy with item to empty optional `() {
        val proxy = OptionalSerializer.OptionalProxy("GenericTestString")
        val opt = OptionalSerializer(Mockito.mock(SerializerFactory::class.java)).fromProxy(proxy)
        assertThat(opt.get(), `is`<Any>("GenericTestString"))
    }
}
