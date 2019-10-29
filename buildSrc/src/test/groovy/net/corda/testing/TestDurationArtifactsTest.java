package net.corda.testing;

import groovy.lang.Tuple2;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TestDurationArtifactsTest {
    final static String CLASSNAME = "FAKE";

    String getXml(List<Tuple2<String, Long>> tests) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<testsuites disabled=\"\" errors=\"\" failures=\"\" name=\"\" tests=\"\" time=\"\">\n" +
                "    <testsuite disabled=\"\" errors=\"\" failures=\"\" hostname=\"\" id=\"\"\n" +
                "               name=\"\" package=\"\" skipped=\"\" tests=\"\" time=\"\" timestamp=\"\">\n" +
                "        <properties>\n" +
                "            <property name=\"\" value=\"\"/>\n" +
                "        </properties>\n");

        for (Tuple2<String, Long> test : tests) {
            Double d = ((double) test.getSecond()) / 1_000_000;
            sb.append("        <testcase assertions=\"\" classname=\"" + CLASSNAME + "\" name=\""
                    + test.getFirst() + "\" status=\"\" time=\"" + d.toString() + "\">\n" +
                    "            <skipped/>\n" +
                    "            <error message=\"\" type=\"\"/>\n" +
                    "            <failure message=\"\" type=\"\"/>\n" +
                    "            <system-out/>\n" +
                    "            <system-err/>\n" +
                    "        </testcase>\n");
        }

        sb.append("        <system-out/>\n" +
                "        <system-err/>\n" +
                "    </testsuite>\n" +
                "</testsuites>");
        return sb.toString();
    }

    String getXmlWithNoTime(List<Tuple2<String, Long>> tests) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<testsuites disabled=\"\" errors=\"\" failures=\"\" name=\"\" tests=\"\" time=\"\">\n" +
                "    <testsuite disabled=\"\" errors=\"\" failures=\"\" hostname=\"\" id=\"\"\n" +
                "               name=\"\" package=\"\" skipped=\"\" tests=\"\" time=\"\" timestamp=\"\">\n" +
                "        <properties>\n" +
                "            <property name=\"\" value=\"\"/>\n" +
                "        </properties>\n");

        for (Tuple2<String, Long> test : tests) {
            Double d = ((double) test.getSecond()) / 1_000_000;
            sb.append("        <testcase assertions=\"\" classname=\"" + CLASSNAME + "\" name=\""
                    + test.getFirst() + "\" status=\"\" time=\"\">\n" +
                    "            <skipped/>\n" +
                    "            <error message=\"\" type=\"\"/>\n" +
                    "            <failure message=\"\" type=\"\"/>\n" +
                    "            <system-out/>\n" +
                    "            <system-err/>\n" +
                    "        </testcase>\n");
        }

        sb.append("        <system-out/>\n" +
                "        <system-err/>\n" +
                "    </testsuite>\n" +
                "</testsuites>");
        return sb.toString();
    }

    @Test
    public void fromJunitXml() {
        List<Tuple2<String, Long>> tests = new ArrayList<>();
        tests.add(new Tuple2<>("TEST-A", 111_000_000L));
        tests.add(new Tuple2<>("TEST-B", 222_200_000L));
        final String xml = getXml(tests);

        List<Tuple2<String, Long>> results
                = TestDurationArtifacts.fromJunitXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        Assert.assertNotNull(results);

        Assert.assertFalse("Should have results", results.isEmpty());
        Assert.assertEquals(results.size(), 2);
        Assert.assertEquals(CLASSNAME + "." + "TEST-A", results.get(0).getFirst());
        Assert.assertEquals(111_000_000L, results.get(0).getSecond().longValue());
        Assert.assertEquals(CLASSNAME + "." + "TEST-B", results.get(1).getFirst());
        Assert.assertEquals(222_200_000L, results.get(1).getSecond().longValue());
    }

    @Test
    public void fromJunitXmlWithZeroDuration() {
        // We do return zero values.
        List<Tuple2<String, Long>> tests = new ArrayList<>();
        tests.add(new Tuple2<>("TEST-A", 0L));
        tests.add(new Tuple2<>("TEST-B", 0L));
        final String xml = getXml(tests);

        List<Tuple2<String, Long>> results
                = TestDurationArtifacts.fromJunitXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        Assert.assertNotNull(results);

        Assert.assertFalse("Should have results", results.isEmpty());
        Assert.assertEquals(results.size(), 2);
        Assert.assertEquals(CLASSNAME + "." + "TEST-A", results.get(0).getFirst());
        Assert.assertEquals(0L, results.get(0).getSecond().longValue());
        Assert.assertEquals(CLASSNAME + "." + "TEST-B", results.get(1).getFirst());
        Assert.assertEquals(0L, results.get(1).getSecond().longValue());
    }

    @Test
    public void fromJunitXmlWithNoDuration() {
        // We do return zero values.
        List<Tuple2<String, Long>> tests = new ArrayList<>();
        tests.add(new Tuple2<>("TEST-A", 0L));
        tests.add(new Tuple2<>("TEST-B", 0L));
        final String xml = getXmlWithNoTime(tests);

        List<Tuple2<String, Long>> results
                = TestDurationArtifacts.fromJunitXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        Assert.assertNotNull(results);

        Assert.assertFalse("Should have results", results.isEmpty());
        Assert.assertEquals(2, results.size());
        Assert.assertEquals(CLASSNAME + "." + "TEST-A", results.get(0).getFirst());
        Assert.assertEquals(0L, results.get(0).getSecond().longValue());
        Assert.assertEquals(CLASSNAME + "." + "TEST-B", results.get(1).getFirst());
        Assert.assertEquals(0L, results.get(1).getSecond().longValue());
    }

    @Test
    public void canCreateZipFile() throws IOException {
        Tests outputTests = new Tests();
        final String testA = "com.corda.testA";
        final String testB = "com.corda.testB";
        outputTests.addDuration(testA, 55L);
        outputTests.addDuration(testB, 33L);

        StringWriter writer = new StringWriter();
        outputTests.write(writer);
        String csv = writer.toString();

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ZipOutputStream outputStream = new ZipOutputStream(byteStream, StandardCharsets.UTF_8)) {
            ZipEntry entry = new ZipEntry("tests.csv");
            outputStream.putNextEntry(entry);
            outputStream.write(csv.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }
        Assert.assertNotEquals(0, byteStream.toByteArray().length);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(byteStream.toByteArray());
        Tests tests = new Tests();
        Assert.assertTrue(tests.isEmpty());

        TestDurationArtifacts.addTestsFromZippedCsv(tests, inputStream);

        Assert.assertFalse(tests.isEmpty());
        Assert.assertEquals(2, tests.size());
        Assert.assertEquals(55L, tests.getDuration(testA));
        Assert.assertEquals(33L, tests.getDuration(testB));

        Assert.assertEquals(44L, tests.getMeanDurationForTests());
    }

    void putIntoArchive(@NotNull final ArchiveOutputStream outputStream,
                        @NotNull final String fileName,
                        @NotNull final String content) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(fileName);
        outputStream.putArchiveEntry(entry);
        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        outputStream.closeArchiveEntry();
    }

    String write(@NotNull final Tests tests) {

        StringWriter writer = new StringWriter();
        tests.write(writer);
        return writer.toString();
    }

    @Test
    public void canCreateZipFileContainingMultipleFiles() throws IOException, ArchiveException {
        //  Currently we don't have two csvs in the zip file, but test anyway.

        Tests outputTests = new Tests();
        final String testA = "com.corda.testA";
        final String testB = "com.corda.testB";
        final String testC = "com.corda.testC";
        outputTests.addDuration(testA, 55L);
        outputTests.addDuration(testB, 33L);

        String csv = write(outputTests);

        Tests otherTests = new Tests();
        otherTests.addDuration(testA, 55L);
        otherTests.addDuration(testB, 33L);
        otherTests.addDuration(testC, 22L);
        String otherCsv = write(otherTests);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ArchiveOutputStream outputStream =
                     new ArchiveStreamFactory("UTF-8").createArchiveOutputStream(ArchiveStreamFactory.ZIP, byteStream)) {
            putIntoArchive(outputStream, "tests1.csv", csv);
            putIntoArchive(outputStream, "tests2.csv", otherCsv);
            outputStream.flush();
        }

        Assert.assertNotEquals(0, byteStream.toByteArray().length);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(byteStream.toByteArray());

        Tests tests = new Tests();
        Assert.assertTrue(tests.isEmpty());

        TestDurationArtifacts.addTestsFromZippedCsv(tests, inputStream);

        Assert.assertFalse(tests.isEmpty());
        Assert.assertEquals(3, tests.size());
        Assert.assertEquals((55 + 33 + 22) / 3, tests.getMeanDurationForTests());
    }

//    // Uncomment to test a file.
//    // Run a build to generate some test files, create a zip:
//    // zip ~/tests.zip  $(find . -name "*.xml" -type f | grep test-results)
////    @Test
////    public void testZipFile() throws FileNotFoundException {
////        File f = new File(System.getProperty("tests.zip", "/tests.zip");
////        List<Tuple2<String, Long>> results = BucketingAllocatorTask.fromZippedXml(new BufferedInputStream(new FileInputStream(f)));
////        Assert.assertFalse("Should have results", results.isEmpty());
////        System.out.println("Results = " + results.size());
////        System.out.println(results.toString());
////    }


    @Test
    public void branchNamesDoNotHaveDirectoryDelimiters() {
        // we use the branch name in file and artifact tagging, so '/' would confuse things,
        // so make sure when we retrieve the property we strip them out.

        final String expected = "release/os/4.3";
        final String key = "git.branch";
        final String cordaType = "corda";
        Properties.setRootProjectType(cordaType);
        System.setProperty(key, expected);

        Assert.assertEquals(expected, System.getProperty(key));
        Assert.assertNotEquals(expected, Properties.getGitBranch());
        Assert.assertEquals("release-os-4.3", Properties.getGitBranch());
    }

    @Test
    public void getTestsFromArtifactory() {
        String artifactory_password = System.getenv("ARTIFACTORY_PASSWORD");
        String artifactory_username = System.getenv("ARTIFACTORY_USERNAME");
        String git_branch = System.getenv("CORDA_GIT_BRANCH");
        String git_target_branch = System.getenv("CORDA_GIT_TARGET_BRANCH");

        if (artifactory_password == null ||
                artifactory_username == null ||
                git_branch == null ||
                git_target_branch == null
        ) {
            System.out.println("Skipping test - set env vars to run this test");
            return;
        }

        System.setProperty("git.branch", git_branch);
        System.setProperty("git.target.branch", git_target_branch);
        System.setProperty("artifactory.password", artifactory_password);
        System.setProperty("artifactory.username", artifactory_username);
        Assert.assertTrue(TestDurationArtifacts.tests.isEmpty());
        TestDurationArtifacts.loadTests();
        Assert.assertFalse(TestDurationArtifacts.tests.isEmpty());
    }

    @Test
    public void tryAndWalkForTestXmlFiles() {
        final String xmlRoot = System.getenv("JUNIT_XML_ROOT");
        if (xmlRoot == null) {
            System.out.println("Set JUNIT_XML_ROOT to run this test");
            return;
        }

        List<Path> testXmlFiles = TestDurationArtifacts.getTestXmlFiles(Paths.get(xmlRoot));
        Assert.assertFalse(testXmlFiles.isEmpty());

        for (Path testXmlFile : testXmlFiles.stream().sorted().collect(Collectors.toList())) {
        //    System.out.println(testXmlFile.toString());
        }

        System.out.println("\n\nTESTS\n\n");
        for (Path testResult : testXmlFiles) {
            try {
                final List<Tuple2<String, Long>> unitTests = TestDurationArtifacts.fromJunitXml(new FileInputStream(testResult.toFile()));
                for (Tuple2<String, Long> unitTest : unitTests) {
                    System.out.println(unitTest.getFirst() + "   -->  " +  BucketingAllocator.getDuration(unitTest.getSecond()));
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
