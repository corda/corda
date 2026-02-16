package net.corda.node.internal;

import net.corda.core.flows.FlowException
import org.junit.Test
import net.corda.node.services.statemachine.ErrorStateTransitionException
import net.corda.node.services.testDefaultFactory
import net.corda.node.services.testSerializationContext
import java.io.NotSerializableException
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.custom.ThrowableSerializer


class ExceptionWithStaticGetter : FlowException() {
    var foo: String = "foobar"
    get() = throw IllegalArgumentException("get")
    set(value) {
        field = value
    }
}

public class ThrowableSerializerTest {
    @Test(expected=NotSerializableException::class)
    fun `See if ErrorStateTransitionException serializes`() {
        val e = ErrorStateTransitionException(ExceptionWithStaticGetter())
        val sf = testDefaultFactory()
        sf.register(ThrowableSerializer(sf))
        val serializedBytes = SerializationOutput(sf).serialize(e, testSerializationContext)
    }
}
