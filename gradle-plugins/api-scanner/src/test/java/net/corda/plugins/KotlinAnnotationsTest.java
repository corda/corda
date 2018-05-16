package net.corda.plugins;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class KotlinAnnotationsTest {
    private static final TemporaryFolder testProjectDir = new TemporaryFolder();
    private static final GradleProject testProject = new GradleProject(testProjectDir, "kotlin-annotations");

    @ClassRule
    public static final TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    private static final String[] expectedClassWithDeprecatedFunctions = {
        "public final class net.corda.example.HasDeprecatedFunctions extends java.lang.Object",
        "  public <init>()",
        "  @NotNull",
        "  public final String doSomething()",
        "##"
    };

    private static final String[] expectedClassWithJvmField = {
        "public final class net.corda.example.HasJvmField extends java.lang.Object",
        "  public <init>()",
        "  @NotNull",
        "  public final String stringValue = \"Hello World\"",
        "##"
    };

    private static final String[] expectedClassWithJvmStaticFunction = {
        "public final class net.corda.example.HasJvmStaticFunction extends java.lang.Object",
        "  public <init>()",
        "  public static final void doThing(String)",
        "  public static final net.corda.example.HasJvmStaticFunction$Companion Companion",
        "##"
    };

    private static final String[] expectedClassWithJvmStaticFunctionCompanion = {
        "public static final class net.corda.example.HasJvmStaticFunction$Companion extends java.lang.Object",
        "  public final void doThing(String)",
        "##"
    };

    private static final String[] expectedClassWithOverloadedConstructor = {
        "public final class net.corda.example.HasOverloadedConstructor extends java.lang.Object",
        "  public <init>()",
        "  public <init>(String)",
        "  public <init>(String, String)",
        "  public <init>(String, String, int)",
        "  @NotNull",
        "  public final String getNotNullable()",
        "  @Nullable",
        "  public final String getNullable()",
        "  public final int getNumber()",
        "##"
    };

    @Test
    public void testDeprecatedAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedClassWithDeprecatedFunctions);
    }

    @Test
    public void testJvmFieldAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedClassWithJvmField);
    }

    @Test
    public void testJvmStaticAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedClassWithJvmStaticFunction)
            .containsSequence(expectedClassWithJvmStaticFunctionCompanion);
    }

    @Test
    public void testJvmOverloadedAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedClassWithOverloadedConstructor);
    }

    @Test
    public void testJvmDefaultAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "public interface net.corda.example.HasDefaultMethod",
                 "  public void doSomething(String)",
                 "##"
            );
    }
}
