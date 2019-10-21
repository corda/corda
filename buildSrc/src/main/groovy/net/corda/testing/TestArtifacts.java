package net.corda.testing;

import groovy.lang.Tuple2;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Zip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

//TODO   DO NOT MODIFY - use TestDurationArtifacts

/**
 * Get or put test artifacts to/from a REST endpoint.  The expected format is a zip file of junit XML files.
 * See https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API
 */
//public class TestArtifacts {
//    private static final Logger LOG = LoggerFactory.getLogger(TestArtifacts.class);
//    private static final String ARTIFACT = "tests";
//    private static final String EXTENSION = "zip";
//    private static final String BASE_URL = "https://software.r3.com/artifactory/corda-test-results/net/corda";
//    private final Artifactory artifactory = new Artifactory();
//
//    /**
//     * The current branch/tag
//     * @return the current branch
//     */
//    @NotNull
//    static String getGitBranch() {
//        return Artifactory.getProperty("git.branch").replace('/', '-');
//    }
//
//    /**
//     * @return the branch that this branch was likely checked out from.
//     */
//    @NotNull
//    static String getTargetGitBranch() {
//        return Artifactory.getProperty("git.target.branch").replace('/', '-');
//    }
//
//    /**
//     * Create the Gradle Zip task to gather junit xml files.
//     *
//     * @param project project to attach this task to
//     * @param name    name of the task
//     * @return a reference to the created task.
//     */
//    @NotNull
//    public static Task createZipTask(Project project, String name) {
//        return project.getTasks().create(name, Zip.class, z -> {
//            z.getArchiveFileName().set(Artifactory.getFileName(ARTIFACT, EXTENSION, getGitBranch()));
//            z.getDestinationDirectory().set(project.getRootDir());
//            z.from(project.getRootDir(), task -> {
//                task.include("**/build/test-results-xml/**/*.xml", "**/build/test-results/**/*.xml");
//            });
//            z.doFirst(task -> project.getLogger().warn("About to zip files"));
//            z.doLast(task -> {
//                project.getLogger().warn("Attempting to upload {}", z.getArchiveFileName().get());
//                try (FileInputStream inputStream = new FileInputStream(new File(z.getArchiveFileName().get()))) {
//                    if (!new TestArtifacts().put(getGitBranch(), inputStream)) {
//                        project.getLogger().warn("Could not upload zip of tests");
//                    }
//                } catch (Exception e) {
//                    project.getLogger().warn("Problem trying to upload: ", z.getArchiveFileName().get(), e);
//                }
//            });
//        });
//    }
//
//    /**
//     * Unzip Junit XML test results in memory and return test names and durations.
//     *
//     * @param inputStream stream containing zipped xml files.
//     * @return list of test name and durations
//     */
//    @NotNull
//    public static List<Tuple2<String, Long>> fromZippedXml(@NotNull InputStream inputStream) {
//        final List<Tuple2<String, Long>> results = new ArrayList<>();
//
//        // We need this because ArchiveStream requires the `mark` functionality which is supported in buffered streams.
//        final BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
//        try (ArchiveInputStream archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(bufferedInputStream)) {
//            ArchiveEntry e;
//            while ((e = archiveInputStream.getNextEntry()) != null) {
//                if (e.isDirectory()) continue;
//
//                // We seem to need to take a copy of the original input stream (as positioned by the ArchiveEntry), because
//                // the XML parsing closes the stream after it has finished.  This has the side effect of only parsing the first
//                // entry in the archive.
//                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//                IOUtils.copy(archiveInputStream, outputStream);
//                ByteArrayInputStream byteInputStream = new ByteArrayInputStream(outputStream.toByteArray());
//
//                List<Tuple2<String, Long>> entryResults = fromJunitXml(byteInputStream);
//                if (entryResults != null) {
//                    results.addAll(entryResults);
//                } else {
//                    LOG.warn("Problem parsing xml in archive file: {}", e.getName());
//                }
//            }
//        } catch (ArchiveException | IOException e) {
//            LOG.warn("Problem unzipping XML test results");
//        }
//
//        LOG.warn("Discovered {} tests", results.size());
//        return results;
//    }
//
//    /**
//     * For a given stream, return the testcase names and durations.
//     * <p>
//     * NOTE:  the input stream will be closed by this method.
//     *
//     * @param inputStream an InputStream, closed once parsed
//     * @return a list of test names and their durations.
//     */
//    @Nullable
//    public static List<Tuple2<String, Long>> fromJunitXml(@NotNull InputStream inputStream) {
//        final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
//        final List<Tuple2<String, Long>> results = new ArrayList<>();
//
//        try {
//            DocumentBuilder builder = dbFactory.newDocumentBuilder();
//            Document document = builder.parse(inputStream);
//            document.getDocumentElement().normalize();
//            XPathFactory xPathfactory = XPathFactory.newInstance();
//            XPath xpath = xPathfactory.newXPath();
//            XPathExpression expression = xpath.compile("//testcase");
//            NodeList nodeList = (NodeList) expression.evaluate(document, XPathConstants.NODESET);
//
//            for (int i = 0; i < nodeList.getLength(); i++) {
//                Node item = nodeList.item(i);
//                NamedNodeMap attributes = item.getAttributes();
//                String testName = attributes.getNamedItem("name") != null ?
//                        attributes.getNamedItem("name").getNodeValue() : "";
//                String testDuration = attributes.getNamedItem("time") != null ?
//                        attributes.getNamedItem("time").getNodeValue() : "";
//                String testClassName = attributes.getNamedItem("classname") != null ?
//                        attributes.getNamedItem("classname").getNodeValue() : "";
//
//                if (!testName.isEmpty() && !testDuration.isEmpty() && !testClassName.isEmpty()) {
//                    long d = (long) (Double.parseDouble(testDuration) * 1000000);
//                    // If a test duration is zero, then don't add it.  When we look up the test later, we'll return the mean test time
//                    // instead.
//                    if (d > 0.0) {
//                        results.add(new Tuple2<>(testClassName + "." + testName, d));
//                    }
//                }
//            }
//        } catch (ParserConfigurationException | IOException | XPathExpressionException | SAXException e) {
//            // Same behaviour as the original CSV reader code.
//            return null;
//        }
//
//        return results;
//    }
//
//    /**
//     * A supplier of tests.
//     * <p>
//     * We get them from Artifactory and then parse the test xml files to get the duration.
//     *
//     * @param destDir destination that we will write the interim tests file to
//     * @return a supplier of test results
//     */
//    @NotNull
//    static Supplier<List<Tuple2<String, Long>>> getTestsSupplier(final File destDir) {
//        return () -> {
//            LOG.warn("Getting tests from Artifactory");
//            try {
//                final TestArtifacts testArtifacts = new TestArtifacts();
//                File tests = testArtifacts.get(getGitBranch(), destDir);
//
//                if (tests == null) {
//                    LOG.warn("Could not get tests from Artifactory for {}, trying {}", getGitBranch(), getTargetGitBranch());
//                    tests = testArtifacts.get(getTargetGitBranch(), destDir);
//                    if (tests == null) {
//                        LOG.warn("Could not get any tests from Artifactory");
//                        return Collections.emptyList();
//                    }
//                }
//                try (FileInputStream inputStream = new FileInputStream(tests)) {
//                    return fromZippedXml(inputStream);
//                }
//            } catch (Exception e) { // was IOException
//                LOG.warn(e.toString());
//                e.printStackTrace();
//                LOG.warn("Could not get tests from Artifactory");
//                return Collections.emptyList();
//            }
//        };
//    }
//
//    /**
//     * Get tests for the specified tag in the outputStream
//     * @param theTag tag for tests
//     * @param outputStream stream of zipped xml files
//     * @return false if we fail to get the tests
//     */
//    public boolean get(@NotNull final String theTag, @NotNull final OutputStream outputStream) {
//        return artifactory.get(BASE_URL, theTag, "tests", "zip", outputStream);
//    }
//
//    /**
//     * Get tests for the specified tag and save as a file
//     * @param theTag tag for tests
//     * @param destDir destination directory
//     * @return the file
//     */
//    @Nullable
//    private File get(@NotNull final String theTag, @NotNull final File destDir) {
//        return artifactory.get(BASE_URL, theTag, "tests", "zip", destDir);
//    }
//
//    /**
//     * Uplaod the supplied tests
//     * @param theTag tag for tests
//     * @param inputStream stream of zipped xml files.
//     * @return true if we succeed
//     */
//    private boolean put(@NotNull final String theTag, @NotNull final InputStream inputStream) {
//        return artifactory.put(BASE_URL, theTag, ARTIFACT, EXTENSION, inputStream);
//    }
//}
//
