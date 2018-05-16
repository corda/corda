package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class MethodWithInternalAnnotationTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "method-internal-annotation");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testMethodWithInternalAnnotations() throws IOException {
        assertThat(testProject.getOutput())
            .contains("net.corda.example.method.InvisibleAnnotation")
            .contains("net.corda.example.method.LocalInvisibleAnnotation");

        assertEquals("public class net.corda.example.method.HasVisibleMethod extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public void hasInvisibleAnnotations()\n" +
            "##", testProject.getApiText());
    }
}
