package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedMethodTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-method");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedMethod() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "  @A",
                "  @B",
                "  @C",
                "  public void hasAnnotation()")
            //Should not include @Deprecated annotation
            .containsSequence(
                "public class net.corda.example.HasDeprecatedMethod extends java.lang.Object",
                "  public <init>()",
                "  public void isDeprecated()"
            );
    }
}
