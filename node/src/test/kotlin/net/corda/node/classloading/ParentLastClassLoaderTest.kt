package net.corda.node.classloading

import net.corda.node.internal.classloading.ParentLastClassLoader
import org.junit.Assert
import org.junit.Test
import java.net.URLClassLoader
import net.corda.core.crypto.SecureHash

class ParentLastClassLoaderTest {
    val urls = (javaClass.classLoader as URLClassLoader).urLs.filter { it.toString().contains("corda") }
    val loader = ParentLastClassLoader(urls.toTypedArray())

    @Test
    fun `correct classloader when loaded via the parent last classloader`() {
        val actualClass = loader.loadClass("net.corda.node.internal.Node")
        val actualClassLoader = actualClass.classLoader

        Assert.assertEquals(loader, actualClassLoader)
    }

    @Test
    fun `default classloader unaffected by parent last classloader`() {
        val expectedLoader = javaClass.classLoader

        // Since we only override classloading for corda classes in this test we find a Corda class to test against
        val objClass = SecureHash.zeroHash.javaClass
        val actualLoader = objClass.classLoader

        Assert.assertEquals(expectedLoader, actualLoader)
    }

    @Test
    fun `non-included classes use the default classloader`() {
        val expectedLoader = javaClass.classLoader

        val objClass = listOf<String>().javaClass
        val actualLoader = objClass.classLoader

        Assert.assertEquals(expectedLoader, actualLoader)
    }

    @Test
    fun `double loading a class should use a cached version`() {
        val first = loader.loadClass("net.corda.node.internal.Node")
        val second = loader.loadClass("net.corda.node.internal.Node")

        Assert.assertEquals(first, second)
    }
}