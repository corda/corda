package net.corda.djvm.execution

import net.corda.djvm.TestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*
import java.util.function.Function

class SandboxEnumTest : TestBase() {
    @Test
    fun `test enum inside sandbox`() = parentedSandbox {
        val contractExecutor = DeterministicSandboxExecutor<Int, Array<String>>(configuration)
        contractExecutor.run<TransformEnum>(0).apply {
            assertThat(result).isEqualTo(arrayOf("ONE", "TWO", "THREE"))
        }
    }

    @Test
    fun `return enum from sandbox`() = parentedSandbox {
        val contractExecutor = DeterministicSandboxExecutor<String, ExampleEnum>(configuration)
        contractExecutor.run<FetchEnum>("THREE").apply {
            assertThat(result).isEqualTo(ExampleEnum.THREE)
        }
    }

    @Test
    fun `test we can identify class as Enum`() = parentedSandbox {
        val contractExecutor = DeterministicSandboxExecutor<ExampleEnum, Boolean>(configuration)
        contractExecutor.run<AssertEnum>(ExampleEnum.THREE).apply {
            assertThat(result).isTrue()
        }
    }

    @Test
    fun `test we can create EnumMap`() = parentedSandbox {
        val contractExecutor = DeterministicSandboxExecutor<ExampleEnum, Int>(configuration)
        contractExecutor.run<UseEnumMap>(ExampleEnum.TWO).apply {
            assertThat(result).isEqualTo(1)
        }
    }

    @Test
    fun `test we can create EnumSet`() = parentedSandbox {
        val contractExecutor = DeterministicSandboxExecutor<ExampleEnum, Boolean>(configuration)
        contractExecutor.run<UseEnumSet>(ExampleEnum.ONE).apply {
            assertThat(result).isTrue()
        }
    }
}


class AssertEnum : Function<ExampleEnum, Boolean> {
    override fun apply(input: ExampleEnum): Boolean {
        return input::class.java.isEnum
    }
}

class TransformEnum : Function<Int, Array<String>> {
    override fun apply(input: Int): Array<String> {
        return ExampleEnum.values().map(ExampleEnum::name).toTypedArray()
    }
}

class FetchEnum : Function<String, ExampleEnum> {
    override fun apply(input: String): ExampleEnum {
        return ExampleEnum.valueOf(input)
    }
}

class UseEnumMap : Function<ExampleEnum, Int> {
    override fun apply(input: ExampleEnum): Int {
        val map = EnumMap<ExampleEnum, String>(ExampleEnum::class.java)
        map[input] = input.name
        return map.size
    }
}

class UseEnumSet : Function<ExampleEnum, Boolean> {
    override fun apply(input: ExampleEnum): Boolean {
        return EnumSet.allOf(ExampleEnum::class.java).contains(input)
    }
}

enum class ExampleEnum {
    ONE, TWO, THREE
}