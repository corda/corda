package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class KotlinLambdasTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "kotlin-lambdas");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testKotlinLambdas() throws IOException {
        assertThat(testProject.getOutput()).contains("net.corda.example.LambdaExpressions$testing$$inlined$schedule$1");
        assertEquals("public final class net.corda.example.LambdaExpressions extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public final void testing(kotlin.Unit)\n" +
            "##", testProject.getApiText());
    }
}
