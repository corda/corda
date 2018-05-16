package net.corda.plugins;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtendedClassTest {
    private static final TemporaryFolder testProjectDir = new TemporaryFolder();
    private static final GradleProject testProject = new GradleProject(testProjectDir, "extended-class");

    @ClassRule
    public static final TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testExtendedClass() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public class net.corda.example.ExtendedClass extends java.io.FilterInputStream",
            "  public <init>(java.io.InputStream)",
            "##");
    }

    @Test
    public void testImplementingClass() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public class net.corda.example.ImplementingClass extends java.lang.Object implements java.io.Closeable",
            "  public <init>()",
            "  public void close()",
            "##");
    }
}
