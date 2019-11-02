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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Get or put test artifacts to/from a REST endpoint.  The expected format is a zip file of junit XML files.
 * See https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API
 */
public class TestDurationArtifacts {
    private static final String EXTENSION = "zip";
    private static final String BASE_URL = "https://software.r3.com/artifactory/corda-test-results/net/corda";
    private static final Logger LOG = LoggerFactory.getLogger(TestDurationArtifacts.class);
    private static final String ARTIFACT = "tests-durations";
    // The one and only set of tests information.  We load these at the start of a build, and update them and save them at the end.
    static Tests tests = new Tests();

    // Artifactory API
    private final Artifactory artifactory = new Artifactory();

    /**
     * Write out the test durations as a CSV file.
     * Reload the tests from artifactory and update with the latest run.
     *
     * @param project project that we are attaching the test to.
     * @param name    basename for the test.
     * @return the csv task
     */
    private static Task createCsvTask(@NotNull final Project project, @NotNull final String name) {
        return project.getTasks().create("createCsvFromXmlFor" + capitalize(name), Task.class, t -> {
            t.setGroup(DistributedTesting.GRADLE_GROUP);
            t.setDescription("Create csv from all discovered junit xml files");

            // Parse all the junit results and write them to a csv file.
            t.doFirst(task -> {
                project.getLogger().warn("About to create CSV file and zip it");

                // Reload the test object from artifactory
                loadTests();
                // Get the list of junit xml artifacts
                final List<Path> testXmlFiles = getTestXmlFiles(project.getBuildDir().getAbsoluteFile().toPath());
                project.getLogger().warn("Found {} xml junit files", testXmlFiles.size());

                //  Read test xml files for tests and duration and add them to the `Tests` object
                //  This adjusts the runCount and over all average duration for existing tests.
                for (Path testResult : testXmlFiles) {
                    try {
                        final List<Tuple2<String, Long>> unitTests = fromJunitXml(new FileInputStream(testResult.toFile()));

                        // Add the non-zero duration tests to build up an average.
                        unitTests.stream()
                                .filter(t2 -> t2.getSecond() > 0L)
                                .forEach(unitTest -> tests.addDuration(unitTest.getFirst(), unitTest.getSecond()));

                        final long meanDurationForTests = tests.getMeanDurationForTests();

                        // Add the zero duration tests using the mean value so they are fairly distributed over the pods in the next run.
                        // If we used 'zero' they would all be added to the smallest bucket.
                        unitTests.stream()
                                .filter(t2 -> t2.getSecond() <= 0L)
                                .forEach(unitTest -> tests.addDuration(unitTest.getFirst(), meanDurationForTests));

                    } catch (FileNotFoundException ignored) {
                    }
                }

                //  Write the test file to disk.
                try {
                    final FileWriter writer = new FileWriter(new File(project.getRootDir(), ARTIFACT + ".csv"));
                    tests.write(writer);
                    LOG.warn("Written tests csv file with {} tests", tests.size());
                } catch (IOException ignored) {
                }
            });
        });
    }

    @NotNull
    static String capitalize(@NotNull final String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1); // groovy has this as an extension method
    }

    /**
     * Discover junit xml files, zip them, and upload to artifactory.
     *
     * @param project root project
     * @param name    task name that we're 'extending'
     * @return gradle task
     */
    @NotNull
    private static Task createJunitZipTask(@NotNull final Project project, @NotNull final String name) {
        return project.getTasks().create("zipJunitXmlFilesAndUploadFor" + capitalize(name), Zip.class, z -> {
            z.setGroup(DistributedTesting.GRADLE_GROUP);
            z.setDescription("Zip junit files and upload to artifactory");

            z.getArchiveFileName().set(Artifactory.getFileName("junit", EXTENSION, getBranchTag()));
            z.getDestinationDirectory().set(project.getRootDir());
            z.setIncludeEmptyDirs(false);
            z.from(project.getRootDir(), task -> task.include("**/build/test-results-xml/**/*.xml", "**/build/test-results/**/*.xml"));
            z.doLast(task -> {
                try (FileInputStream inputStream = new FileInputStream(new File(z.getArchiveFileName().get()))) {
                    new Artifactory().put(BASE_URL, getBranchTag(), "junit", EXTENSION, inputStream);
                } catch (Exception ignored) {
                }
            });
        });
    }

    /**
     * Zip and upload test-duration csv files to artifactory
     *
     * @param project root project that we're attaching the task to
     * @param name    the task name we're 'extending'
     * @return gradle task
     */
    @NotNull
    private static Task createCsvZipAndUploadTask(@NotNull final Project project, @NotNull final String name) {
        return project.getTasks().create("zipCsvFilesAndUploadFor" + capitalize(name), Zip.class, z -> {
            z.setGroup(DistributedTesting.GRADLE_GROUP);
            z.setDescription("Zips test duration csv and uploads to artifactory");

            z.getArchiveFileName().set(Artifactory.getFileName(ARTIFACT, EXTENSION, getBranchTag()));
            z.getDestinationDirectory().set(project.getRootDir());
            z.setIncludeEmptyDirs(false);

            // There's only one csv, but glob it anyway.
            z.from(project.getRootDir(), task -> task.include("**/" + ARTIFACT + ".csv"));

            // ...base class method zips the CSV...

            z.doLast(task -> {
                //  We've now created the one csv file containing the tests and their mean durations,
                //  this task has zipped it, so we now just upload it.
                project.getLogger().warn("SAVING tests");
                project.getLogger().warn("Attempting to upload {}", z.getArchiveFileName().get());
                try (FileInputStream inputStream = new FileInputStream(new File(z.getArchiveFileName().get()))) {
                    if (!new TestDurationArtifacts().put(getBranchTag(), inputStream)) {
                        project.getLogger().warn("Could not upload zip of tests");
                    } else {
                        project.getLogger().warn("SAVED tests");
                    }
                } catch (Exception e) {
                    project.getLogger().warn("Problem trying to upload: {} {}", z.getArchiveFileName().get(), e.toString());
                }
            });
        });
    }

    /**
     * Create the Gradle Zip task to gather test information
     *
     * @param project project to attach this task to
     * @param name    name of the task
     * @param task    a task that we depend on when creating the csv so Gradle produces the correct task graph.
     * @return a reference to the created task.
     */
    @NotNull
    public static Task createZipTask(@NotNull final Project project, @NotNull final String name, @Nullable final Task task) {
        final Task zipJunitTask = createJunitZipTask(project, name);
        final Task csvTask = createCsvTask(project, name);
        csvTask.dependsOn(zipJunitTask);
        // For debugging - can be removed - this simply gathers junit xml and uploads them to artifactory
        // so that we can inspect them.

        if (task != null) {
            csvTask.dependsOn(task);
        }
        final Task zipCsvTask = createCsvZipAndUploadTask(project, name);
        zipCsvTask.dependsOn(csvTask); // we have to create the csv before we can zip it.

        return zipCsvTask;
    }

    static List<Path> getTestXmlFiles(@NotNull final Path rootDir) {
        List<Path> paths = new ArrayList<>();
        List<PathMatcher> matchers = new ArrayList<>();
        matchers.add(FileSystems.getDefault().getPathMatcher("glob:**/test-results-xml/**/*.xml"));
        try {
            Files.walkFileTree(rootDir, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    for (PathMatcher matcher : matchers) {
                        if (matcher.matches(file)) {
                            paths.add(file);
                            break;
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.warn("Could not walk tree and get all test xml files:  {}", e.toString());
        }
        return paths;
    }

    /**
     * Unzip test results in memory and return test names and durations.
     * Assumes the input stream contains only csv files of the correct format.
     *
     * @param tests             reference to the Tests object to be populated.
     * @param zippedInputStream stream containing zipped result file(s)
     */
    static void addTestsFromZippedCsv(@NotNull final Tests tests,
                                      @NotNull final InputStream zippedInputStream) {
        // We need this because ArchiveStream requires the `mark` functionality which is supported in buffered streams.
        final BufferedInputStream bufferedInputStream = new BufferedInputStream(zippedInputStream);
        try (ArchiveInputStream archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(bufferedInputStream)) {
            ArchiveEntry e;
            while ((e = archiveInputStream.getNextEntry()) != null) {
                if (e.isDirectory()) continue;

                // We seem to need to take a copy of the original input stream (as positioned by the ArchiveEntry), because
                // the XML parsing closes the stream after it has finished.  This has the side effect of only parsing the first
                // entry in the archive.
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                IOUtils.copy(archiveInputStream, outputStream);
                ByteArrayInputStream byteInputStream = new ByteArrayInputStream(outputStream.toByteArray());

                // Read the tests from the (csv) stream
                final InputStreamReader reader = new InputStreamReader(byteInputStream);

                // Add the tests to the Tests object
                tests.addTests(Tests.read(reader));
            }
        } catch (ArchiveException | IOException e) {
            LOG.warn("Problem unzipping XML test results");
        }

        LOG.debug("Discovered {} tests", tests.size());
    }

    /**
     * For a given stream, return the testcase names and durations.
     * <p>
     * NOTE:  the input stream will be closed by this method.
     *
     * @param inputStream an InputStream, closed once parsed
     * @return a list of test names and their durations in nanos.
     */
    @NotNull
    static List<Tuple2<String, Long>> fromJunitXml(@NotNull final InputStream inputStream) {
        final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        final List<Tuple2<String, Long>> results = new ArrayList<>();

        try {
            final DocumentBuilder builder = dbFactory.newDocumentBuilder();
            final Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();
            final XPathFactory xPathfactory = XPathFactory.newInstance();
            final XPath xpath = xPathfactory.newXPath();
            final XPathExpression expression = xpath.compile("//testcase");
            final NodeList nodeList = (NodeList) expression.evaluate(document, XPathConstants.NODESET);

            final BiFunction<NamedNodeMap, String, String> get =
                    (a, k) -> a.getNamedItem(k) != null ? a.getNamedItem(k).getNodeValue() : "";

            for (int i = 0; i < nodeList.getLength(); i++) {
                final Node item = nodeList.item(i);
                final NamedNodeMap attributes = item.getAttributes();
                final String testName = get.apply(attributes, "name");
                final String testDuration = get.apply(attributes, "time");
                final String testClassName = get.apply(attributes, "classname");

                // If the test doesn't have a duration (it should), we return zero.
                if (!(testName.isEmpty() || testClassName.isEmpty())) {
                    final long nanos = !testDuration.isEmpty() ? (long) (Double.parseDouble(testDuration) * 1000000.0) : 0L;
                    results.add(new Tuple2<>(testClassName + "." + testName, nanos));
                } else {
                    LOG.warn("Bad test in junit xml:  name={}  className={}", testName, testClassName);
                }
            }
        } catch (ParserConfigurationException | IOException | XPathExpressionException | SAXException e) {
            return Collections.emptyList();
        }

        return results;
    }

    /**
     * A supplier of tests.
     * <p>
     * We get them from Artifactory and then parse the test xml files to get the duration.
     *
     * @return a supplier of test results
     */
    @NotNull
    static Supplier<Tests> getTestsSupplier() {
        return TestDurationArtifacts::loadTests;
    }

    /**
     * we need to prepend the project type so that we have a unique tag for artifactory
     *
     * @return
     */
    static String getBranchTag() {
        return (Properties.getRootProjectType() + "-" + Properties.getGitBranch()).replace('.', '-');
    }

    /**
     * we need to prepend the project type so that we have a unique tag artifactory
     *
     * @return
     */
    static String getTargetBranchTag() {
        return (Properties.getRootProjectType() + "-" + Properties.getTargetGitBranch()).replace('.', '-');
    }

    /**
     * Load the tests from Artifactory, in-memory.  No temp file used.  Existing test data is cleared.
     *
     * @return a reference to the loaded tests.
     */
    static Tests loadTests() {
        LOG.warn("LOADING previous test runs from Artifactory");
        tests.clear();
        try {
            final TestDurationArtifacts testArtifacts = new TestDurationArtifacts();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            //  Try getting artifacts for our branch, if not, try the target branch.
            if (!testArtifacts.get(getBranchTag(), outputStream)) {
                outputStream = new ByteArrayOutputStream();
                LOG.warn("Could not get tests from Artifactory for tag {}, trying {}", getBranchTag(), getTargetBranchTag());
                if (!testArtifacts.get(getTargetBranchTag(), outputStream)) {
                    LOG.warn("Could not get any tests from Artifactory");
                    return tests;
                }
            }

            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            addTestsFromZippedCsv(tests, inputStream);
            LOG.warn("Got {} tests from Artifactory", tests.size());
            return tests;
        } catch (Exception e) { // was IOException
            LOG.warn(e.toString());
            LOG.warn("Could not get tests from Artifactory");
            return tests;
        }
    }

    /**
     * Get tests for the specified tag in the outputStream
     *
     * @param theTag       tag for tests
     * @param outputStream stream of zipped xml files
     * @return false if we fail to get the tests
     */
    private boolean get(@NotNull final String theTag, @NotNull final OutputStream outputStream) {
        return artifactory.get(BASE_URL, theTag, ARTIFACT, "zip", outputStream);
    }

    /**
     * Upload the supplied tests
     *
     * @param theTag      tag for tests
     * @param inputStream stream of zipped xml files.
     * @return true if we succeed
     */
    private boolean put(@NotNull final String theTag, @NotNull final InputStream inputStream) {
        return artifactory.put(BASE_URL, theTag, ARTIFACT, EXTENSION, inputStream);
    }
}

