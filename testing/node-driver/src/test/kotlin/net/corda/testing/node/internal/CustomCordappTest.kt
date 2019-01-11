package net.corda.testing.node.internal

import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.get
import net.corda.core.internal.inputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.jar.JarInputStream

class CustomCordappTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `packageAsJar writes out the CorDapp info into the manifest`() {
        val cordapp = cordappWithPackages("net.corda.testing.node.internal").copy(targetPlatformVersion = 123, name = "CustomCordappTest")
        val jarFile = packageAsJar(cordapp)
        JarInputStream(jarFile.inputStream()).use {
            assertThat(it.manifest[CordappImpl.TARGET_PLATFORM_VERSION]).isEqualTo("123")
            assertThat(it.manifest[CordappImpl.CORDAPP_CONTRACT_NAME]).isEqualTo("CustomCordappTest")
            assertThat(it.manifest[CordappImpl.CORDAPP_WORKFLOW_NAME]).isEqualTo("CustomCordappTest")
        }
    }

    @Test
    fun `packageAsJar on leaf package`() {
        val entries = packageAsJarThenReadBack(cordappWithPackages("net.corda.testing.node.internal"))

        assertThat(entries).contains(
                "net/corda/testing/node/internal/CustomCordappTest.class",
                "net/corda/testing/node/internal/resource.txt" // Make sure non-class resource files are also picked up
        ).doesNotContain(
                "net/corda/testing/node/MockNetworkTest.class"
        )

        // Make sure the MockNetworkTest class does actually exist to ensure the above is not a false-positive
        assertThat(javaClass.classLoader.getResource("net/corda/testing/node/MockNetworkTest.class")).isNotNull()
    }

    @Test
    fun `packageAsJar on package with sub-packages`() {
        val entries = packageAsJarThenReadBack(cordappWithPackages("net.corda.testing.node"))

        assertThat(entries).contains(
                "net/corda/testing/node/internal/CustomCordappTest.class",
                "net/corda/testing/node/internal/resource.txt",
                "net/corda/testing/node/MockNetworkTest.class"
        )
    }

    @Test
    fun `packageAsJar on single class`() {
        val entries = packageAsJarThenReadBack(cordappForClasses(InternalMockNetwork::class.java))

        assertThat(entries).containsOnly("${InternalMockNetwork::class.java.name.replace('.', '/')}.class")
    }

    private fun packageAsJar(cordapp: CustomCordapp): Path {
        val jarFile = tempFolder.newFile().toPath()
        cordapp.packageAsJar(jarFile)
        return jarFile
    }

    private fun packageAsJarThenReadBack(cordapp: CustomCordapp): List<String> {
        val jarFile = packageAsJar(cordapp)
        val entries = ArrayList<String>()
        JarInputStream(jarFile.inputStream()).use {
            while (true) {
                val e = it.nextJarEntry ?: break
                entries += e.name
                it.closeEntry()
            }
        }
        return entries
    }
}
