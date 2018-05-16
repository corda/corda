package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class BasicInterfaceTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "basic-interface");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testBasicInterface() throws IOException {
        assertEquals(
            "public interface net.corda.example.BasicInterface\n" +
            "  public abstract java.math.BigInteger getBigNumber()\n" +
            "##", testProject.getApiText());
    }
}
