package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeCustomisedEnumTest : TestBase(KOTLIN) {
    @ParameterizedTest
    @EnumSource(UserRole::class)
    fun `test deserialize enum with custom toString`(role: UserRole) {
        val userEnumData = UserEnumData(role)
        val data = userEnumData.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxData = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showUserEnumData = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowUserEnumData::class.java)
            val result = showUserEnumData.apply(sandboxData) ?: fail("Result cannot be null")

            assertEquals(ShowUserEnumData().apply(userEnumData), result.toString())
            assertEquals("UserRole: name='${role.roleName}', ordinal='${role.ordinal}'", result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowUserEnumData : Function<UserEnumData, String> {
        override fun apply(input: UserEnumData): String {
            return with(input) {
                "UserRole: name='${role.roleName}', ordinal='${role.ordinal}'"
            }
        }
    }
}

interface Role {
    val roleName: String
}

@Suppress("unused")
@CordaSerializable
enum class UserRole(override val roleName: String) : Role {
    CONTROLLER(roleName = "Controller"),
    WORKER(roleName = "Worker");

    override fun toString() = roleName
}

@CordaSerializable
data class UserEnumData(val role: UserRole)
