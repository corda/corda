package net.corda.node

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.fail

class DeterministicSourcesRule : TestRule {
    private var deterministicRt: Path? = null
    private var deterministicSources: List<Path>? = null

    val bootstrap: Path get() = deterministicRt ?: fail("deterministic-rt.path property not set")
    val corda: List<Path> get() = deterministicSources ?: fail("deterministic-sources.path property not set")

    override fun apply(statement: Statement, description: Description?): Statement {
        deterministicRt = System.getProperty("deterministic-rt.path")?.run { Paths.get(this) }
        deterministicSources = System.getProperty("deterministic-sources.path")?.split(File.pathSeparator)
            ?.map { Paths.get(it) }
            ?.filter { Files.exists(it) }

        return object : Statement() {
            override fun evaluate() {
                statement.evaluate()
            }
        }
    }
}