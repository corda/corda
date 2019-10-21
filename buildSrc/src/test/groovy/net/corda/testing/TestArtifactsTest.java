package net.corda.testing;

import groovy.lang.Tuple2;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TestArtifactsTest {
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

    @Test
    public void fromJunitXml() {
        List<Tuple2<String, Long>> tests = new ArrayList<>();
        tests.add(new Tuple2<>("TEST-A", 111_000_000L));
        tests.add(new Tuple2<>("TEST-B", 222_200_000L));
        final String xml = getXml(tests);

        List<Tuple2<String, Long>> results
                = TestArtifacts.fromJunitXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        double delta = 0.001;
        Assert.assertNotNull(results);

        Assert.assertFalse("Should have results", results.isEmpty());
        Assert.assertEquals(results.size(), 2);
        Assert.assertEquals(results.get(0).getFirst(), CLASSNAME + "." + "TEST-A");
        Assert.assertEquals(results.get(0).getSecond(), 111_000_000L, delta);
        Assert.assertEquals(results.get(1).getFirst(), CLASSNAME + "." + "TEST-B");
        Assert.assertEquals(results.get(1).getSecond(), 222_200_000L, delta);
    }

    @Test
    public void canCreateZipFile() throws IOException, ArchiveException {
        List<Tuple2<String, Long>> tests = new ArrayList<>();
        tests.add(new Tuple2<>("TEST-A", 111_000_000L));
        tests.add(new Tuple2<>("TEST-B", 222_200_000L));
        final String xml = getXml(tests);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ZipOutputStream outputStream = new ZipOutputStream(byteStream, StandardCharsets.UTF_8)) {
            ZipEntry entry = new ZipEntry("tests.xml");
            outputStream.putNextEntry(entry);
            outputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }
        Assert.assertNotEquals(0, byteStream.toByteArray().length);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(byteStream.toByteArray());
        List<Tuple2<String, Long>> results = TestArtifacts.fromZippedXml(inputStream);

        Assert.assertNotNull(results);

        double delta = 0.001;
        Assert.assertFalse("Should have results", results.isEmpty());
        Assert.assertEquals(results.size(), 2);
        Assert.assertEquals(results.get(0).getFirst(), CLASSNAME + "." + "TEST-A");
        Assert.assertEquals(results.get(0).getSecond().longValue(), 111_000_000L);
        Assert.assertEquals(results.get(1).getFirst(), CLASSNAME + "." + "TEST-B");
        Assert.assertEquals(results.get(1).getSecond().longValue(), 222_200_000L);
    }

    void putXml(ArchiveOutputStream outputStream, String fileName, String xml) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(fileName);
        outputStream.putArchiveEntry(entry);
        outputStream.write(xml.getBytes(StandardCharsets.UTF_8));
        outputStream.closeArchiveEntry();
    }

    @Test
    public void canCreateZipFileContainingMultipleFiles() throws IOException, ArchiveException {

        List<Tuple2<String, Long>> tests = new ArrayList<>();
        tests.add(new Tuple2<>("TEST-A", 111_000_000L));
        tests.add(new Tuple2<>("TEST-B", 222_200_000L));
        final String xml = getXml(tests);

        List<Tuple2<String, Long>> tests2 = new ArrayList<>();
        tests2.add(new Tuple2<>("TEST-C", 333_333_000L));
        tests2.add(new Tuple2<>("TEST-D", 4_444_444_400L));
        final String xml2 = getXml(tests2);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ArchiveOutputStream outputStream =
                     new ArchiveStreamFactory("UTF-8").createArchiveOutputStream(ArchiveStreamFactory.ZIP, byteStream)) {
            putXml(outputStream, "tests1.xml", xml);
            putXml(outputStream, "tests2.xml", xml2);
            outputStream.flush();
        }

        Assert.assertNotEquals(0, byteStream.toByteArray().length);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(byteStream.toByteArray());

        List<Tuple2<String, Long>> results = TestArtifacts.fromZippedXml(inputStream);

        Assert.assertNotNull(results);

        double delta = 0.001;
        Assert.assertFalse("Should have results", results.isEmpty());
        Assert.assertEquals(4, results.size());
        Assert.assertEquals(results.get(0).getFirst(), CLASSNAME + "." + "TEST-A");
        Assert.assertEquals(results.get(0).getSecond().longValue(), 111_000_000L);
        Assert.assertEquals(results.get(1).getFirst(), CLASSNAME + "." + "TEST-B");
        Assert.assertEquals(results.get(1).getSecond().longValue(), 222_200_000L);
        Assert.assertEquals(results.get(2).getFirst(), CLASSNAME + "." + "TEST-C");
        Assert.assertEquals(results.get(2).getSecond().longValue(), 333_333_000L);
        Assert.assertEquals(results.get(3).getFirst(), CLASSNAME + "." + "TEST-D");
        Assert.assertEquals(results.get(3).getSecond().longValue(), 4_444_444_400L);
    }

    // Uncomment to test a file.
    // Run a build to generate some test files, create a zip:
    // zip ~/tests.zip  $(find . -name "*.xml" -type f | grep test-results)
//    @Test
//    public void testZipFile() throws FileNotFoundException {
//        File f = new File(System.getProperty("tests.zip", "/tests.zip");
//        List<Tuple2<String, Long>> results = BucketingAllocatorTask.fromZippedXml(new BufferedInputStream(new FileInputStream(f)));
//        Assert.assertFalse("Should have results", results.isEmpty());
//        System.out.println("Results = " + results.size());
//        System.out.println(results.toString());
//    }

    @Test
    public void branchNamesDoNotHaveDirectoryDelimiters() {
        // we use the branch name in file and artifact tagging, so '/' would confuse things,
        // so make sure when we retrieve the property we strip them out.

        final String expected = "release/os/4.3";
        final String key = "git.branch";

        System.setProperty(key, expected);

        Assert.assertEquals(expected, System.getProperty(key));
        Assert.assertNotEquals(expected, TestArtifacts.getGitBranch());
        Assert.assertEquals("release-os-4.3", TestArtifacts.getGitBranch());
    }
}
