package net.corda.djvm.source

import net.corda.djvm.analysis.ClassResolver
import net.corda.djvm.analysis.Whitelist
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class SourceClassLoaderTest {

    private val classResolver = ClassResolver(Whitelist.EMPTY, Whitelist.EMPTY, "")

    @Test
    fun `can load class from Java's lang package when no files are provided to the class loader`() {
        val classLoader = SourceClassLoader(emptyList(), classResolver)
        val clazz = classLoader.loadClass("java.lang.Boolean")
        assertThat(clazz.simpleName).isEqualTo("Boolean")
    }

    @Test(expected = ClassNotFoundException::class)
    fun `cannot load arbitrary class when no files are provided to the class loader`() {
        val classLoader = SourceClassLoader(emptyList(), classResolver)
        classLoader.loadClass("net.foo.NonExistentClass")
    }

    @Test
    fun `can load class when JAR file is provided to the class loader`() {
        useTemporaryFile("jar-with-single-class.jar") {
            val classLoader = SourceClassLoader(this, classResolver)
            val clazz = classLoader.loadClass("net.foo.Bar")
            assertThat(clazz.simpleName).isEqualTo("Bar")
        }
    }

    @Test(expected = ClassNotFoundException::class)
    fun `cannot load arbitrary class when JAR file is provided to the class loader`() {
        useTemporaryFile("jar-with-single-class.jar") {
            val classLoader = SourceClassLoader(this, classResolver)
            classLoader.loadClass("net.foo.NonExistentClass")
        }
    }

    @Test
    fun `can load classes when multiple JAR files are provided to the class loader`() {
        useTemporaryFile("jar-with-single-class.jar", "jar-with-two-classes.jar") {
            val classLoader = SourceClassLoader(this, classResolver)
            val firstClass = classLoader.loadClass("com.somewhere.Test")
            assertThat(firstClass.simpleName).isEqualTo("Test")
            val secondClass = classLoader.loadClass("com.somewhere.AnotherTest")
            assertThat(secondClass.simpleName).isEqualTo("AnotherTest")
        }
    }

    @Test(expected = ClassNotFoundException::class)
    fun `cannot load arbitrary class when multiple JAR files are provided to the class loader`() {
        useTemporaryFile("jar-with-single-class.jar", "jar-with-two-classes.jar") {
            val classLoader = SourceClassLoader(this, classResolver)
            classLoader.loadClass("com.somewhere.NonExistentClass")
        }
    }

    @Test
    fun `can load class when folder containing JAR file is provided to the class loader`() {
        useTemporaryFile("jar-with-single-class.jar", "jar-with-two-classes.jar") {
            val (first, second) = this
            val directory = first.parent
            val classLoader = SourceClassLoader(listOf(directory), classResolver)
            assertThat(classLoader.resolvedUrls).anySatisfy {
                assertThat(it).isEqualTo(first.toUri().toURL())
            }.anySatisfy {
                assertThat(it).isEqualTo(second.toUri().toURL())
            }
        }
    }

    companion object {

        private fun useTemporaryFile(vararg resourceNames: String, action: List<Path>.() -> Unit) {
            val paths = resourceNames.map { resourceName ->
                val stream = SourceClassLoaderTest::class.java.getResourceAsStream("/$resourceName")
                        ?: throw Exception("Cannot find resource \"$resourceName\"")
                Files.createTempFile("source-class-loader", ".jar").apply {
                    Files.newOutputStream(this).use {
                        stream.copyTo(it)
                    }
                }
            }
            action(paths)
            paths.forEach { Files.deleteIfExists(it) }
        }

    }

}