package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class KotlinInternalAnnotationTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "kotlin-internal-annotation");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testKotlinInternalAnnotation() throws IOException {
        assertThat(testProject.getOutput()).contains("net.corda.example.kotlin.CordaInternal");
        assertEquals(
            "public final class net.corda.example.kotlin.AnnotatedClass extends java.lang.Object\n" +
            "  public <init>()\n" +
            "##", testProject.getApiText());
    }
}
