package net.corda.testing;

import com.fasterxml.jackson.databind.annotation.JsonAppend;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PropertiesTest {
    private static String username = "me";
    private static String password = "me";
    private static String cordaType = "corda-project";
    private static String branch = "mine";
    private static String targetBranch = "master";

    @Before
    public void setUp() {
        System.setProperty("git.branch", branch);
        System.setProperty("git.target.branch", targetBranch);
        System.setProperty("artifactory.username", username);
        System.setProperty("artifactory.password", password);
    }

    @After
    public void tearDown() {
        System.setProperty("git.branch", "");
        System.setProperty("git.target.branch", "");
        System.setProperty("artifactory.username", "");
        System.setProperty("artifactory.password", "");
    }

    @Test
    public void cordaType() {
    Properties.setCordaType(cordaType);
        Assert.assertEquals(cordaType, Properties.getCordaType());
    }

    @Test
    public void getUsername() {
        Assert.assertEquals(username, Properties.getUsername());
    }

    @Test
    public void getPassword() {
        Assert.assertEquals(password, Properties.getPassword());
    }

    @Test
    public void getGitBranch() {
        Assert.assertEquals(branch, Properties.getGitBranch());
    }

    @Test
    public void getTargetGitBranch() {
        Assert.assertEquals(targetBranch, Properties.getTargetGitBranch());
    }
}