package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.*
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.Calendar.FEBRUARY
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.*
import java.util.zip.ZipFile

class MetaFixTimestampTest {
    companion object {
        private val testProjectDir = TemporaryFolder()
        private val sourceJar = DummyJar(testProjectDir, MetaFixTimestampTest::class.java, "timestamps")

        private val CONSTANT_TIME: FileTime = FileTime.fromMillis(
            GregorianCalendar(1980, FEBRUARY, 1).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.timeInMillis
        )

        private lateinit var metafixedJar: Path
        private lateinit var output: String

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(sourceJar)
            .around(createTestProject())

        private fun createTestProject() = TestRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    testProjectDir.installResource("gradle.properties")
                    testProjectDir.newFile("build.gradle").writeText("""
plugins {
    id 'net.corda.plugins.jar-filter'
}

import net.corda.gradle.jarfilter.MetaFixerTask
task metafix(type: MetaFixerTask) {
    jars file("${sourceJar.path.toUri()}")
    preserveTimestamps = false
}
""")
                    val result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments(getGradleArgsForTasks("metafix"))
                        .withPluginClasspath()
                        .build()
                    output = result.output
                    println(output)

                    val metafix = result.task(":metafix")
                        ?: throw AssertionError("No outcome for metafix task")
                    assertEquals(SUCCESS, metafix.outcome)

                    metafixedJar = testProjectDir.pathOf("build", "metafixer-libs", "timestamps-metafixed.jar")
                    assertThat(metafixedJar).isRegularFile()

                    base.evaluate()
                }
            }
        }

        private val ZipEntry.methodName: String get() = if (method == STORED) "Stored" else "Deflated"
    }

    @Test
    fun fileTimestampsAreRemoved() {
        var directoryCount = 0
        var classCount = 0
        var otherCount = 0

        ZipFile(metafixedJar.toFile()).use { jar ->
            for (entry in jar.entries()) {
                println("Entry: ${entry.name}")
                println("- ${entry.methodName} (${entry.size} size / ${entry.compressedSize} compressed) bytes")
                assertThat(entry.lastModifiedTime).isEqualTo(CONSTANT_TIME)
                assertThat(entry.lastAccessTime).isNull()
                assertThat(entry.creationTime).isNull()

                if (entry.isDirectory) {
                    ++directoryCount
                } else if (entry.name.endsWith(".class")) {
                    ++classCount
                } else {
                    ++otherCount
                }
            }
        }

        assertThat(directoryCount).isGreaterThan(0)
        assertThat(classCount).isGreaterThan(0)
        assertThat(otherCount).isGreaterThan(0)
    }
}
