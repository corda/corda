package net.corda.testing;

import org.junit.Assert;
import org.junit.Test;

public class TestDurationArtifactsTest {
    //     Uncomment and change path to check the file code works ok.
    @Test
    public void tryAndWalkForTestXmlFiles() {
//        List<Path> testXmlFiles = TestDurationArtifacts.getTestXmlFiles(
//                Paths.get(System.getProperty("project.root"), "/corda-os"));
//        Assert.assertFalse(testXmlFiles.isEmpty());
//
//        for (Path testXmlFile : testXmlFiles) {
//            System.out.println(testXmlFile.toString());
//        }
    }

    @Test
    public void branchNamesDoNotHaveDirectoryDelimiters() {
        // we use the branch name in file and artifact tagging, so '/' would confuse things,
        // so make sure when we retrieve the property we strip them out.

        final String expected = "release/os/4.3";
        final String key = "git.branch";

        System.setProperty(key, expected);

        Assert.assertEquals(expected, System.getProperty(key));
        Assert.assertNotEquals(expected, TestDurationArtifacts.getGitBranch());
        Assert.assertEquals("release-os-4.3", TestDurationArtifacts.getGitBranch());
    }

}