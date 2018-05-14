package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class ExtendedInterfaceTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "extended-interface");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testExtendedInterface() throws IOException {
        assertEquals(
            "public interface net.corda.example.ExtendedInterface extends java.util.concurrent.Future\n" +
            "  public abstract String getName()\n" +
            "  public abstract void setName(String)\n" +
            "##", testProject.getApiText());
    }
}
