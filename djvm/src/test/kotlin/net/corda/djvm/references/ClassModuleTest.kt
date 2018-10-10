package net.corda.djvm.references

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ClassModuleTest {

    private val module = ClassModule()

    @Test
    fun `can detect arrays from type descriptors`() {
        assertThat(module.isArray("java/lang/String")).isFalse()
        assertThat(module.isArray("[Ljava/lang/String;")).isTrue()
        assertThat(module.isArray("[[[Ljava/lang/String;")).isTrue()
    }

    @Test
    fun `can derive full source locations`() {
        assertThat(module.getFullSourceLocation(clazz<ClassModuleTest>()))
                .isEqualTo("net/corda/djvm/references/ClassModuleTest.kt")
    }

    @Test
    fun `can get binary class name from string`() {
        assertThat(module.getBinaryClassName("Foo")).isEqualTo("Foo")
        assertThat(module.getBinaryClassName("com.foo.Bar")).isEqualTo("com/foo/Bar")
        assertThat(module.getBinaryClassName("com/foo/Bar")).isEqualTo("com/foo/Bar")
    }

    @Test
    fun `can get formatted class name from string`() {
        assertThat(module.getFormattedClassName("Foo")).isEqualTo("Foo")
        assertThat(module.getFormattedClassName("com/foo/Bar")).isEqualTo("com.foo.Bar")
        assertThat(module.getFormattedClassName("com.foo.Bar")).isEqualTo("com.foo.Bar")
    }

    @Test
    fun `can get shortened class name from string`() {
        assertThat(module.getShortName("Foo")).isEqualTo("Foo")
        assertThat(module.getShortName("com/foo/Bar")).isEqualTo("Bar")
        assertThat(module.getShortName("com.foo.Bar")).isEqualTo("Bar")
    }

    @Test
    fun `can derive normalized class names from binary representation`() {
        assertThat(module.normalizeClassName("")).isEqualTo("")
        assertThat(module.normalizeClassName('Z')).isEqualTo("java/lang/Boolean")
        assertThat(module.normalizeClassName('B')).isEqualTo("java/lang/Byte")
        assertThat(module.normalizeClassName('C')).isEqualTo("java/lang/Character")
        assertThat(module.normalizeClassName('S')).isEqualTo("java/lang/Short")
        assertThat(module.normalizeClassName('I')).isEqualTo("java/lang/Integer")
        assertThat(module.normalizeClassName('J')).isEqualTo("java/lang/Long")
        assertThat(module.normalizeClassName('F')).isEqualTo("java/lang/Float")
        assertThat(module.normalizeClassName('D')).isEqualTo("java/lang/Double")
        assertThat(module.normalizeClassName('X')).isEqualTo("X")
        assertThat(module.normalizeClassName("XXX")).isEqualTo("XXX")
        assertThat(module.normalizeClassName("Ljava/util/Random;")).isEqualTo("java/util/Random")
        assertThat(module.normalizeClassName("[Ljava/util/Random;")).isEqualTo("java/util/Random[]")
        assertThat(module.normalizeClassName("[[Ljava/util/Random;")).isEqualTo("java/util/Random[][]")
    }

    private inline fun <reified T> clazz() =
            T::class.java.let {
                val className = module.getBinaryClassName(it.name)
                ClassRepresentation(0, 0, className, className, sourceFile = "${it.simpleName}.kt")
            }

}