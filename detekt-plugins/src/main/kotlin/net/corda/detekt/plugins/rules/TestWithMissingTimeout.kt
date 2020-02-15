package net.corda.detekt.plugins.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotationEntry

class TestWithMissingTimeout : Rule() {

    override val issue = Issue(
            javaClass.simpleName,
            Severity.Security,
            "This rule reports a test case with a missing timeout field in the @Test annotation",
            Debt.FIVE_MINS
    )

    // Checks that the test is using JUnit 4 by looking at the imports in the file. This is a little questionable, but this information is
    // otherwise not exposed.
    private fun isTestAnnotationJunit4(annotationEntry: KtAnnotationEntry): Boolean {
        return annotationEntry.containingKtFile.importDirectives.any { it.importPath?.fqName?.asString() == "org.junit.Test" }
    }

    // Look at all annotations. If they are named "Test", and the corresponding file has a JUnit 4 Test import in, assume that all these
    // annotations are JUnit Tests and report if they do not have a "timeout" argument.
    // Note that this doesn't apply to JUnit 5 as the test annotation does not take a timeout argument in this case.
    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        if (annotationEntry.shortName.toString() == "Test" && isTestAnnotationJunit4(annotationEntry)) {
            val params = annotationEntry.valueArguments
            if (params.none { it.getArgumentName()?.asName?.identifier == "timeout" }) {
                report(CodeSmell(issue, Entity.from(annotationEntry), "Missing timeout parameter from Test annotation"))
            }
        }
        super.visitAnnotationEntry(annotationEntry)
    }
}