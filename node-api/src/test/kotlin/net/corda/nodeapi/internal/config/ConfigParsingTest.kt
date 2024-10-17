package net.corda.nodeapi.internal.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory.empty
import com.typesafe.config.ConfigRenderOptions.defaults
import com.typesafe.config.ConfigValueFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.URL
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.Properties
import java.util.UUID
import javax.security.auth.x500.X500Principal
import kotlin.io.path.div
import kotlin.reflect.full.primaryConstructor

class ConfigParsingTest {
    @Test(timeout=300_000)
	fun String() {
        testPropertyType<StringData, StringListData, String>("hello world!", "bye")
    }

    @Test(timeout=300_000)
	fun Int() {
        testPropertyType<IntData, IntListData, Int>(1, 2)
    }

    @Test(timeout=300_000)
	fun Long() {
        testPropertyType<LongData, LongListData, Long>(Long.MAX_VALUE, Long.MIN_VALUE)
    }

    @Test(timeout=300_000)
	fun Double() {
        testPropertyType<DoubleData, DoubleListData, Double>(1.2, 3.4)
    }

    @Test(timeout=300_000)
	fun Boolean() {
        testPropertyType<BooleanData, BooleanListData, Boolean>(true, false)
        assertThat(config(Pair("value", "false")).parseAs<BooleanData>().value).isEqualTo(false)
        assertThat(config(Pair("value", "False")).parseAs<BooleanData>().value).isEqualTo(false)
        assertThat(config(Pair("value", "FALSE")).parseAs<BooleanData>().value).isEqualTo(false)
        assertThat(config(Pair("value", "true")).parseAs<BooleanData>().value).isEqualTo(true)
        assertThat(config(Pair("value", "True")).parseAs<BooleanData>().value).isEqualTo(true)
        assertThat(config(Pair("value", "TRUE")).parseAs<BooleanData>().value).isEqualTo(true)
        assertThatThrownBy { config(Pair("value", "stilton")).parseAs<BooleanData>().value }
                .isInstanceOf(ConfigException.WrongType::class.java)
                .hasMessageContaining("hardcoded value: value has type STRING rather than BOOLEAN")
    }
    @Test(timeout=300_000)
	fun Enum() {
        testPropertyType<EnumData, EnumListData, TestEnum>(TestEnum.Value2, TestEnum.Value1, valuesToString = true)
    }

    @Test(timeout=300_000)
	fun `unknown Enum`() {
        val config = config("value" to "UnknownValue")
        assertThatThrownBy { config.parseAs<EnumData>() }
                .hasMessageContaining(TestEnum.Value1.name)
                .hasMessageContaining(TestEnum.Value2.name)
    }

    @Test(timeout=300_000)
	fun LocalDate() {
        testPropertyType<LocalDateData, LocalDateListData, LocalDate>(LocalDate.now(), LocalDate.now().plusDays(1), valuesToString = true)
    }

    @Test(timeout=300_000)
	fun Instant() {
        testPropertyType<InstantData, InstantListData, Instant>(Instant.now(), Instant.now().plusMillis(100), valuesToString = true)
    }

    @Test(timeout=300_000)
	fun NetworkHostAndPort() {
        testPropertyType<NetworkHostAndPortData, NetworkHostAndPortListData, NetworkHostAndPort>(
                NetworkHostAndPort("localhost", 2223),
                NetworkHostAndPort("localhost", 2225),
                valuesToString = true)
    }

    @Test(timeout=300_000)
	fun Path() {
        val path = Path.of("tmp", "test")
        testPropertyType<PathData, PathListData, Path>(path, path / "file", valuesToString = true)
    }

    @Test(timeout=300_000)
	fun URL() {
        testPropertyType<URLData, URLListData, URL>(URL("http://localhost:1234"), URL("http://localhost:1235"), valuesToString = true)
    }

    @Test(timeout=300_000)
	fun X500Principal() {
        testPropertyType<X500PrincipalData, X500PrincipalListData, X500Principal>(X500Principal("C=US, L=New York, CN=Corda Root CA, OU=Corda, O=R3 HoldCo LLC"), X500Principal("O=Bank A,L=London,C=GB"), valuesToString = true)
    }

    @Test(timeout=300_000)
	fun UUID() {
        testPropertyType<UUIDData, UUIDListData, UUID>(UUID.randomUUID(), UUID.randomUUID(), valuesToString = true)
    }

    @Test(timeout=300_000)
	fun `test CordaX500Name`() {
        val name1 = CordaX500Name(organisation = "Mock Party", locality = "London", country = "GB")
        testPropertyType<CordaX500NameData, CordaX500NameListData, CordaX500Name>(
                name1,
                CordaX500Name(organisation = "Mock Party 2", locality = "London", country = "GB"),
                valuesToString = true)

        // Test with config object.
        val config = config("value" to mapOf("organisation" to "Mock Party", "locality" to "London", "country" to "GB"))
        assertThat(config.parseAs<CordaX500NameData>().value).isEqualTo(name1)
    }

    @Test(timeout=300_000)
	fun `flat Properties`() {
        val config = config("value" to mapOf("key" to "prop"))
        val data = PropertiesData(Properties().apply { this["key"] = "prop" })
        assertThat(config.parseAs<PropertiesData>()).isEqualTo(data)
        assertThat(data.toConfig()).isEqualTo(config)
    }

    @Test(timeout=300_000)
	fun `Properties key with dot`() {
        val config = config("value" to mapOf("key.key2" to "prop"))
        val data = PropertiesData(Properties().apply { this["key.key2"] = "prop" })
        assertThat(config.parseAs<PropertiesData>().value).isEqualTo(data.value)
    }

    @Test(timeout=300_000)
	fun `nested Properties`() {
        val config = config("value" to mapOf("first" to mapOf("second" to "prop")))
        val data = PropertiesData(Properties().apply { this["first.second"] = "prop" })
        assertThat(config.parseAs<PropertiesData>().value).isEqualTo(data.value)
        assertThat(data.toConfig()).isEqualTo(config)
    }

    @Test(timeout=300_000)
	fun `List of Properties`() {
        val config = config("values" to listOf(emptyMap(), mapOf("key" to "prop")))
        val data = PropertiesListData(listOf(
                Properties(),
                Properties().apply { this["key"] = "prop" }))
        assertThat(config.parseAs<PropertiesListData>().values).isEqualTo(data.values)
        assertThat(data.toConfig()).isEqualTo(config)
    }

    @Test(timeout=300_000)
	fun Set() {
        val data = StringSetData(setOf("a", "b"))
        assertThat(config("values" to listOf("a", "a", "b")).parseAs<StringSetData>()).isEqualTo(data)
        assertThat(data.toConfig()).isEqualTo(config("values" to listOf("a", "b")))
        assertThat(empty().parseAs<StringSetData>().values).isEmpty()
    }

    @Test(timeout=300_000)
	fun `multi property data class`() {
        val config = config(
                "b" to true,
                "i" to 123,
                "l" to listOf("a", "b"))
        val data = config.parseAs<MultiPropertyData>()
        assertThat(data.i).isEqualTo(123)
        assertThat(data.b).isTrue()
        assertThat(data.l).containsExactly("a", "b")
        assertThat(data.toConfig()).isEqualTo(config)
    }

    @Test(timeout=300_000)
	fun `nested data classes`() {
        val config = config(
                "first" to mapOf(
                        "value" to "nested"))
        val data = NestedData(StringData("nested"))
        assertThat(config.parseAs<NestedData>()).isEqualTo(data)
        assertThat(data.toConfig()).isEqualTo(config)
    }

    @Test(timeout=300_000)
	fun `List of data classes`() {
        val config = config(
                "list" to listOf(
                        mapOf("value" to "1"),
                        mapOf("value" to "2")))
        val data = DataListData(listOf(StringData("1"), StringData("2")))
        assertThat(config.parseAs<DataListData>()).isEqualTo(data)
        assertThat(data.toConfig()).isEqualTo(config)
    }

    @Test(timeout=300_000)
	fun `default value property`() {
        assertThat(config("a" to 3).parseAs<DefaultData>()).isEqualTo(DefaultData(3, 2))
        assertThat(config("a" to 3, "defaultOfTwo" to 3).parseAs<DefaultData>()).isEqualTo(DefaultData(3, 3))
        assertThat(DefaultData(3).toConfig()).isEqualTo(config("a" to 3, "defaultOfTwo" to 2))
    }

    @Test(timeout=300_000)
	fun `nullable property`() {
        assertThat(empty().parseAs<NullableData>().nullable).isNull()
        assertThat(config("nullable" to null).parseAs<NullableData>().nullable).isNull()
        assertThat(config("nullable" to "not null").parseAs<NullableData>().nullable).isEqualTo("not null")
        assertThat(NullableData(null).toConfig()).isEqualTo(empty())
    }

    @Test(timeout=300_000)
	fun `data class with checks`() {
        val config = config("positive" to -1)
        assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { config.parseAs<PositiveData>() }
                .withMessageContaining("-1")
    }

    @Test(timeout=300_000)
	fun `old config property`() {
        assertThat(config("oldValue" to "old").parseAs<OldData>().newValue).isEqualTo("old")
        assertThat(config("newValue" to "new").parseAs<OldData>().newValue).isEqualTo("new")
        assertThat(OldData("old").toConfig()).isEqualTo(config("newValue" to "old"))
    }

    @Test(timeout=300_000)
	fun `static field`() {
        assertThat(DataWithCompanion(3).toConfig()).isEqualTo(config("value" to 3))
    }

    @Test(timeout=300_000)
	fun `unknown configuration keys raise exception`() {

        // intentional typo here, parsing should throw rather than sneakily return default value
        val knownKey = "mandatory"
        val unknownKey = "optioal"
        val configuration = config(knownKey to "hello", unknownKey to "world")

        assertThatThrownBy { configuration.parseAs<TypedConfiguration>() }.isInstanceOfSatisfying(UnknownConfigurationKeysException::class.java) { exception ->

            assertThat(exception.unknownKeys).contains(unknownKey)
            assertThat(exception.unknownKeys).doesNotContain(knownKey)
        }
    }

    @Test(timeout=300_000)
	fun `parse with provided parser`() {
        val type1Config = mapOf("type" to "1", "value" to "type 1 value")
        val type2Config = mapOf("type" to "2", "value" to "type 2 value")

        val configuration = config("values" to listOf(type1Config, type2Config))
        val objects = configuration.parseAs<TestObjects>()

        assertThat(objects.values).containsExactly(TestObject.Type1("type 1 value"), TestObject.Type2("type 2 value"))
    }

    class TestParser : ConfigParser<TestObject> {
        override fun parse(config: Config): TestObject {
            val type = config.getInt("type")
            return when (type) {
                1 -> config.parseAs<TestObject.Type1>(onUnknownKeys = UnknownConfigKeysPolicy.IGNORE::handle)
                2 -> config.parseAs<TestObject.Type2>(onUnknownKeys = UnknownConfigKeysPolicy.IGNORE::handle)
                else -> throw IllegalArgumentException("Unsupported Object type : '$type'")
            }
        }
    }

    data class TestObjects(val values: List<TestObject>)

    @CustomConfigParser(parser = TestParser::class)
    sealed class TestObject {
        data class Type1(val value: String) : TestObject()
        data class Type2(val value: String) : TestObject()
    }

    private inline fun <reified S : SingleData<V>, reified L : ListData<V>, V : Any> testPropertyType(
            value1: V,
            value2: V,
            valuesToString: Boolean = false) {
        testSingleProperty<S, V>(value1, valuesToString)
        testListProperty<L, V>(value1, value2, valuesToString)
    }

    private inline fun <reified T : SingleData<V>, V : Any> testSingleProperty(value: V, valueToString: Boolean) {
        val constructor = T::class.primaryConstructor!!
        val config = config("value" to if (valueToString) value.toString() else value)
        val data = constructor.call(value)
        assertThat(config.parseAs<T>().value).isEqualTo(data.value)
        assertThat(data.toConfig()).isEqualTo(config)
    }

    private inline fun <reified T : ListData<V>, V : Any> testListProperty(value1: V, value2: V, valuesToString: Boolean) {
        val rawValues = listOf(value1, value2)
        val configValues = if (valuesToString) rawValues.map(Any::toString) else rawValues
        val constructor = T::class.primaryConstructor!!
        for (n in 0..2) {
            val config = config("values" to configValues.take(n))
            val data = constructor.call(rawValues.take(n))
            assertThat(config.parseAs<T>().values).isEqualTo(data.values)
            assertThat(data.toConfig()).isEqualTo(config)
        }
        assertThat(empty().parseAs<T>().values).isEmpty()
    }

    private fun config(vararg values: Pair<String, *>): Config {
        val config = ConfigValueFactory.fromMap(mapOf(*values))
        println(config.render(defaults().setOriginComments(false)))
        return config.toConfig()
    }

    private interface SingleData<out T> {
        val value: T
    }

    private interface ListData<out T> {
        val values: List<T>
    }

    data class TypedConfiguration(private val mandatory: String, private val optional: String = "optional")
    data class StringData(override val value: String) : SingleData<String>
    data class StringListData(override val values: List<String>) : ListData<String>
    data class StringSetData(val values: Set<String>)
    data class IntData(override val value: Int) : SingleData<Int>
    data class IntListData(override val values: List<Int>) : ListData<Int>
    data class LongData(override val value: Long) : SingleData<Long>
    data class LongListData(override val values: List<Long>) : ListData<Long>
    data class DoubleData(override val value: Double) : SingleData<Double>
    data class DoubleListData(override val values: List<Double>) : ListData<Double>
    data class BooleanData(override val value: Boolean) : SingleData<Boolean>
    data class BooleanListData(override val values: List<Boolean>) : ListData<Boolean>
    data class EnumData(override val value: TestEnum) : SingleData<TestEnum>
    data class EnumListData(override val values: List<TestEnum>) : ListData<TestEnum>
    data class LocalDateData(override val value: LocalDate) : SingleData<LocalDate>
    data class LocalDateListData(override val values: List<LocalDate>) : ListData<LocalDate>
    data class InstantData(override val value: Instant) : SingleData<Instant>
    data class InstantListData(override val values: List<Instant>) : ListData<Instant>
    data class NetworkHostAndPortData(override val value: NetworkHostAndPort) : SingleData<NetworkHostAndPort>
    data class NetworkHostAndPortListData(override val values: List<NetworkHostAndPort>) : ListData<NetworkHostAndPort>
    data class PathData(override val value: Path) : SingleData<Path>
    data class PathListData(override val values: List<Path>) : ListData<Path>
    data class URLData(override val value: URL) : SingleData<URL>
    data class URLListData(override val values: List<URL>) : ListData<URL>
    data class X500PrincipalData(override val value: X500Principal) : SingleData<X500Principal>
    data class X500PrincipalListData(override val values: List<X500Principal>) : ListData<X500Principal>
    data class UUIDData(override val value: UUID) : SingleData<UUID>
    data class UUIDListData(override val values: List<UUID>) : ListData<UUID>
    data class CordaX500NameData(override val value: CordaX500Name) : SingleData<CordaX500Name>
    data class CordaX500NameListData(override val values: List<CordaX500Name>) : ListData<CordaX500Name>
    data class PropertiesData(override val value: Properties) : SingleData<Properties>
    data class PropertiesListData(override val values: List<Properties>) : ListData<Properties>
    data class MultiPropertyData(val i: Int, val b: Boolean, val l: List<String>)
    data class NestedData(val first: StringData)
    data class DataListData(val list: List<StringData>)
    data class DefaultData(val a: Int, val defaultOfTwo: Int = 2)
    data class NullableData(val nullable: String?)
    data class PositiveData(private val positive: Int) {
        init {
            require(positive > 0) { "$positive is not positive" }
        }
    }

    data class OldData(
            @OldConfig("oldValue")
            val newValue: String)

    data class DataWithCompanion(val value: Int) {
        companion object {
            @Suppress("unused")
            val companionValue = 2
        }
    }

    enum class TestEnum { Value1, Value2 }
}
