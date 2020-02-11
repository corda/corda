package net.corda.serialization.djvm

import greymalkin.ExternalEnum
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NonEmptySet
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.EnumSet
import java.util.NavigableSet
import java.util.SortedSet
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeCollectionsTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing string list`() {
        val stringList = StringList(listOf("Hello", "World", "!"))
        val data = stringList.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxList = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showStringList = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowStringList::class.java)
            val result = showStringList.apply(sandboxList) ?: fail("Result cannot be null")

            assertEquals(stringList.lines.joinToString(), result.toString())
            assertEquals("Hello, World, !", result.toString())
        }
    }

    class ShowStringList : Function<StringList, String> {
        override fun apply(data: StringList): String {
            return data.lines.joinToString()
        }
    }

    @Test
	fun `test deserializing integer set`() {
        val integerSet = IntegerSet(linkedSetOf(10, 3, 15, 2, 10))
        val data = integerSet.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxSet = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showIntegerSet = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowIntegerSet::class.java)
            val result = showIntegerSet.apply(sandboxSet) ?: fail("Result cannot be null")

            assertEquals(integerSet.numbers.joinToString(), result.toString())
            assertEquals("10, 3, 15, 2", result.toString())
        }
    }

    class ShowIntegerSet : Function<IntegerSet, String> {
        override fun apply(data: IntegerSet): String {
            return data.numbers.joinToString()
        }
    }

    @Test
	fun `test deserializing integer sorted set`() {
        val integerSortedSet = IntegerSortedSet(sortedSetOf(10, 15, 1000, 3, 2, 10))
        val data = integerSortedSet.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxSet = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showIntegerSortedSet = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowIntegerSortedSet::class.java)
            val result = showIntegerSortedSet.apply(sandboxSet) ?: fail("Result cannot be null")

            assertEquals(integerSortedSet.numbers.joinToString(), result.toString())
            assertEquals("2, 3, 10, 15, 1000", result.toString())
        }
    }

    class ShowIntegerSortedSet : Function<IntegerSortedSet, String> {
        override fun apply(data: IntegerSortedSet): String {
            return data.numbers.joinToString()
        }
    }

    @Test
	fun `test deserializing long navigable set`() {
        val longNavigableSet = LongNavigableSet(sortedSetOf(99955L, 10, 15, 1000, 3, 2, 10))
        val data = longNavigableSet.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxSet = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showLongNavigableSet = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowLongNavigableSet::class.java)
            val result = showLongNavigableSet.apply(sandboxSet) ?: fail("Result cannot be null")

            assertEquals(longNavigableSet.numbers.joinToString(), result.toString())
            assertEquals("2, 3, 10, 15, 1000, 99955", result.toString())
        }
    }

    class ShowLongNavigableSet : Function<LongNavigableSet, String> {
        override fun apply(data: LongNavigableSet): String {
            return data.numbers.joinToString()
        }
    }

    @Test
	fun `test deserializing short collection`() {
        val shortCollection = ShortCollection(listOf(10, 200, 3000))
        val data = shortCollection.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxCollection = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showShortCollection = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowShortCollection::class.java)
            val result = showShortCollection.apply(sandboxCollection) ?: fail("Result cannot be null")

            assertEquals(shortCollection.numbers.joinToString(), result.toString())
            assertEquals("10, 200, 3000", result.toString())
        }
    }

    class ShowShortCollection : Function<ShortCollection, String> {
        override fun apply(data: ShortCollection): String {
            return data.numbers.joinToString()
        }
    }

    @Test
	fun `test deserializing non-empty string set`() {
        val nonEmptyStrings = NonEmptyStringSet(NonEmptySet.of("Hello", "World", "!"))
        val data = nonEmptyStrings.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxSet = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showNonEmptyStringSet = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowNonEmptyStringSet::class.java)
            val result = showNonEmptyStringSet.apply(sandboxSet) ?: fail("Result cannot be null")

            assertEquals(nonEmptyStrings.lines.joinToString(), result.toString())
            assertEquals("Hello, World, !", result.toString())
        }
    }

    class ShowNonEmptyStringSet : Function<NonEmptyStringSet, String> {
        override fun apply(data: NonEmptyStringSet): String {
            return data.lines.joinToString()
        }
    }

    @Test
	fun `test deserializing enum set`() {
        val enumSet = HasEnumSet(EnumSet.of(ExternalEnum.DOH))
        val data = enumSet.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxSet = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showHasEnumSet = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowHasEnumSet::class.java)
            val result = showHasEnumSet.apply(sandboxSet) ?: fail("Result cannot be null")

            assertEquals(enumSet.values.toString(), result.toString())
            assertEquals("[DOH]", result.toString())
        }
    }

    class ShowHasEnumSet : Function<HasEnumSet, String> {
        override fun apply(data: HasEnumSet): String {
            return data.values.toString()
        }
    }
}

@CordaSerializable
class StringList(val lines: List<String>)

@CordaSerializable
class IntegerSet(val numbers: Set<Int>)

@CordaSerializable
class IntegerSortedSet(val numbers: SortedSet<Int>)

@CordaSerializable
class LongNavigableSet(val numbers: NavigableSet<Long>)

@CordaSerializable
class ShortCollection(val numbers: Collection<Short>)

@CordaSerializable
class NonEmptyStringSet(val lines: NonEmptySet<String>)

@CordaSerializable
class HasEnumSet(val values: EnumSet<ExternalEnum>)
