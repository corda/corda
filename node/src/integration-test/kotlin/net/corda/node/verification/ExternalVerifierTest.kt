package net.corda.node.verification

import io.github.classgraph.ClassGraph
import net.corda.core.internal.pooledScan
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ExternalVerifierTest {
    @Test(timeout=300_000)
    fun `external verifier does not have newer Kotlin`() {
        val kotlinClasses = ClassGraph()
                .overrideClasspath(javaClass.getResource("external-verifier.jar")!!)
                .enableAnnotationInfo()
                .pooledScan()
                .use { result ->
                    result.getClassesWithAnnotation(Metadata::class.java).associateBy({ it.name }, {
                        val annotationInfo = it.getAnnotationInfo(Metadata::class.java)
                        val metadataVersion = annotationInfo.parameterValues.get("mv").value as IntArray
                        "${metadataVersion[0]}.${metadataVersion[1]}"
                    })
                }

        // First make sure we're capturing the right data
        assertThat(kotlinClasses).containsKeys("net.corda.verifier.ExternalVerifier")
        // Kotlin metadata version 1.1 maps to language versions 1.0 to 1.3
        val newerKotlinClasses = kotlinClasses.filterValues { metadataVersion -> metadataVersion != "1.1" }
        assertThat(newerKotlinClasses).isEmpty()
    }
}
