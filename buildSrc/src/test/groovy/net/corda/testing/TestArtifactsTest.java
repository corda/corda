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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class TestArtifactsTest {
    final static String CLASSNAME = "FAKE";

    String getXml(List<Tuple2<String, Double>> tests) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<testsuites disabled=\"\" errors=\"\" failures=\"\" name=\"\" tests=\"\" time=\"\">\n" +
                "    <testsuite disabled=\"\" errors=\"\" failures=\"\" hostname=\"\" id=\"\"\n" +
                "               name=\"\" package=\"\" skipped=\"\" tests=\"\" time=\"\" timestamp=\"\">\n" +
                "        <properties>\n" +
                "            <property name=\"\" value=\"\"/>\n" +
                "        </properties>\n");

        for (Tuple2<String, Double> test : tests) {
            sb.append("        <testcase assertions=\"\" classname=\"" + CLASSNAME + "\" name=\""
                    + test.getFirst() + "\" status=\"\" time=\"" + test.getSecond().toString() + "\">\n" +
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
        List<Tuple2<String, Double>> tests = new ArrayList<>();
        tests.add(new Tuple2<>("TEST-A", 111.0));
        tests.add(new Tuple2<>("TEST-B", 222.2));
        final String xml = getXml(tests);

        List<Tuple2<String, Double>> results
                = TestArtifacts.fromJunitXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        double delta = 0.001;
        Assert.assertNotNull(results);

        Assert.assertFalse("Should have results", results.isEmpty());
        Assert.assertEquals(results.size(), 2);
        Assert.assertEquals(results.get(0).getFirst(), CLASSNAME + "." + "TEST-A");
        Assert.assertEquals(results.get(0).getSecond(), 111, delta);
        Assert.assertEquals(results.get(1).getFirst(), CLASSNAME + "." + "TEST-B");
        Assert.assertEquals(results.get(1).getSecond(), 222.2, delta);
    }

    @Test
    public void canCreateZipFile() throws IOException, ArchiveException {
        List<Tuple2<String, Double>> tests = new ArrayList<>();
        tests.add(new Tuple2<>("TEST-A", 111.0));
        tests.add(new Tuple2<>("TEST-B", 222.2));
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
        List<Tuple2<String, Double>> results = TestArtifacts.fromZippedXml(inputStream);

        Assert.assertNotNull(results);

        double delta = 0.001;
        Assert.assertFalse("Should have results", results.isEmpty());
        Assert.assertEquals(results.size(), 2);
        Assert.assertEquals(results.get(0).getFirst(), CLASSNAME + "." + "TEST-A");
        Assert.assertEquals(results.get(0).getSecond(), 111, delta);
        Assert.assertEquals(results.get(1).getFirst(), CLASSNAME + "." + "TEST-B");
        Assert.assertEquals(results.get(1).getSecond(), 222.2, delta);
    }

    void putXml(ArchiveOutputStream outputStream, String fileName, String xml) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(fileName);
        outputStream.putArchiveEntry(entry);
        outputStream.write(xml.getBytes(StandardCharsets.UTF_8));
        outputStream.closeArchiveEntry();
    }

    @Test
    public void canCreateZipFileContainingMultipleFiles() throws IOException, ArchiveException {

        List<Tuple2<String, Double>> tests = new ArrayList<>();
        tests.add(new Tuple2<>("TEST-A", 111.0));
        tests.add(new Tuple2<>("TEST-B", 222.2));
        final String xml = getXml(tests);

        List<Tuple2<String, Double>> tests2 = new ArrayList<>();
        tests2.add(new Tuple2<>("TEST-C", 333.333));
        tests2.add(new Tuple2<>("TEST-D", 4444.4444));
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

        List<Tuple2<String, Double>> results = TestArtifacts.fromZippedXml(inputStream);

        Assert.assertNotNull(results);

        double delta = 0.001;
        Assert.assertFalse("Should have results", results.isEmpty());
        Assert.assertEquals(4, results.size());
        Assert.assertEquals(results.get(0).getFirst(), CLASSNAME + "." + "TEST-A");
        Assert.assertEquals(results.get(0).getSecond(), 111, delta);
        Assert.assertEquals(results.get(1).getFirst(), CLASSNAME + "." + "TEST-B");
        Assert.assertEquals(results.get(1).getSecond(), 222.2, delta);
        Assert.assertEquals(results.get(2).getFirst(), CLASSNAME + "." + "TEST-C");
        Assert.assertEquals(results.get(2).getSecond(), 333.333, delta);
        Assert.assertEquals(results.get(3).getFirst(), CLASSNAME + "." + "TEST-D");
        Assert.assertEquals(results.get(3).getSecond(), 4444.4444, delta);
    }

    // Uncomment to test a file.
    // Run a build to generate some test files, create a zip:
    // zip ~/tests.zip  $(find . -name "*.xml" -type f | grep test-results)
//    @Test
//    public void testZipFile() throws FileNotFoundException {
//        File f = new File("/Users/barrylapthorn/tests.zip");
//        List<Tuple2<String, Double>> results = BucketingAllocatorTask.fromZippedXml(new BufferedInputStream(new FileInputStream(f)));
//        Assert.assertFalse("Should have results", results.isEmpty());
//        System.out.println("Results = " + results.size());
//        System.out.println(results.toString());
//    }

}
