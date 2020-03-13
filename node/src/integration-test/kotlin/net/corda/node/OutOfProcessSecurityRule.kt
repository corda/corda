package net.corda.node

import org.assertj.core.api.Assertions.assertThat
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Path
import java.nio.file.Paths

class OutOfProcessSecurityRule(private val securityPolicy: Path) : TestRule {
     constructor() : this(Paths.get("out-process-node.policy"))

    val systemProperties: Map<String, String> = mapOf(
        "test.gradle.user.home" to System.getProperty("test.gradle.user.home"),
        "test.rootProject.uri" to System.getProperty("test.rootProject.uri"),
        "java.security.policy" to "=${securityPolicy.toUri()}",
        "java.security.manager" to ""
    )

    override fun apply(statement: Statement, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                @Suppress("UsePropertyAccessSyntax")
                assertThat(securityPolicy).isReadable()
                statement.evaluate()
            }
        }
    }
}