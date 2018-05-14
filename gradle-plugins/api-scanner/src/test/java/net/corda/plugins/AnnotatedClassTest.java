package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedClassTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-class");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedClass() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "@NotInherited",
                "public class net.corda.example.HasInheritedAnnotation extends java.lang.Object")
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "public class net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation")
            .containsSequence(
                "@DoNotImplement",
                "@AnAnnotation",
                "public class net.corda.example.DoNotImplementAnnotation extends java.lang.Object");
    }
}
