package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class BasicClassTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "basic-class");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testBasicClass() throws IOException {
        assertEquals(
            "public class net.corda.example.BasicClass extends java.lang.Object\n" +
            "  public <init>(String)\n" +
            "  public String getName()\n" +
            "##", testProject.getApiText());
    }
}
