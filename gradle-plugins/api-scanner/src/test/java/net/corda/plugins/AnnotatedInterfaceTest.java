package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedInterfaceTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-interface");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedInterface() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "@NotInherited",
                "public interface net.corda.example.HasInheritedAnnotation")
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "public interface net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation")
            .containsSequence(
                "@DoNotImplement",
                "@AnAnnotation",
                "public interface net.corda.example.DoNotImplementAnnotation"
            );
    }
}
