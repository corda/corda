package net.corda.serialization.djvm

import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.time.ZoneId
import java.util.function.Function
import java.util.stream.Stream

@ExtendWith(LocalSerialization::class)
class DeserializeZoneIdTest : TestBase(KOTLIN) {
    class ZoneIdProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return ZoneId.getAvailableZoneIds().stream()
                .sorted().limit(10).map { Arguments.of(ZoneId.of(it)) }
        }
    }

    @ArgumentsSource(ZoneIdProvider::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun `test deserializing zone id`(zoneId: ZoneId) {
        val data = zoneId.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxZoneId = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showZoneId = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowZoneId::class.java)
            val result = showZoneId.apply(sandboxZoneId) ?: fail("Result cannot be null")

            assertEquals(zoneId.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowZoneId : Function<ZoneId, String> {
        override fun apply(zoneId: ZoneId): String {
            return zoneId.toString()
        }
    }
}
