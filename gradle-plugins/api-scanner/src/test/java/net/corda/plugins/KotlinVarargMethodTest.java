package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class KotlinVarargMethodTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "kotlin-vararg-method");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    private static final String[] expectedInterfaceWithVarargMethod = {
        "public interface net.corda.example.KotlinVarargMethod",
        "  public abstract void action(int...)",
        "##"
    };

    private static final String[] expectedInterfaceWithArrayVarargMethod = {
            "public interface net.corda.example.KotlinVarargArrayMethod",
            "  public abstract void action(String[]...)",
            "##"
    };

    @Test
    public void testKotlinVarargMethod() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedInterfaceWithVarargMethod)
            .containsSequence(expectedInterfaceWithArrayVarargMethod);
    }
}