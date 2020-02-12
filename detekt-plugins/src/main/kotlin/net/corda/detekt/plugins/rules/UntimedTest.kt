package net.corda.detekt.plugins.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotationEntry

class UntimedTest : Rule() {

    override val issue = Issue(
            javaClass.simpleName,
            Severity.Security,
            "This rule reports a test case with a missing timeout field in the @Test annotation",
            Debt.FIVE_MINS
    )

    private fun isTestAnnotationJunit4(annotationEntry: KtAnnotationEntry): Boolean {
        return annotationEntry.containingKtFile.importDirectives.any { it.importPath?.fqName?.asString() == "org.junit.Test" }
    }

    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        if (annotationEntry.shortName.toString() == "Test" && isTestAnnotationJunit4(annotationEntry)) {
            val params = annotationEntry.valueArguments
            if (params.filter { it.getArgumentName()?.asName?.identifier == "timeout" }.isEmpty()) {
                report(CodeSmell(issue, Entity.from(annotationEntry), "Missing timeout parameter from Test annotation"))
            }
        }
        super.visitAnnotationEntry(annotationEntry)
    }
}