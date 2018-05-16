package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class FieldWithInternalAnnotationTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "field-internal-annotation");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testFieldWithInternalAnnotations() throws IOException {
        assertThat(testProject.getOutput())
            .contains("net.corda.example.field.InvisibleAnnotation")
            .contains("net.corda.example.field.LocalInvisibleAnnotation");
        assertEquals("public class net.corda.example.field.HasVisibleField extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public String hasInvisibleAnnotations\n" +
            "##", testProject.getApiText());
    }
}
