package net.corda.testing;

import groovy.lang.Tuple2;
import groovy.lang.Tuple3;
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
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Get or put test artifacts to/from a REST endpoint.  The expected format is a zip file of junit XML files.
 * See https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API
 */
public class TestDurationArtifacts {
    private static final Logger LOG = LoggerFactory.getLogger(TestDurationArtifacts.class);
    private static final String ARTIFACT = "tests-durations";
    private static final String EXTENSION = "zip";
    private static final String BASE_URL = "https://software.r3.com/artifactory/corda-test-results/net/corda";
    private final Artifactory artifactory = new Artifactory();

    /**
     * The current branch/tag
     *
     * @return the current branch
     */
    @NotNull
    static String getGitBranch() {
        return Artifactory.getProperty("git.branch").replace('/', '-');
    }

    /**
     * @return the branch that this branch was likely checked out from.
     */
    @NotNull
    static String getTargetGitBranch() {
        return Artifactory.getProperty("git.target.branch").replace('/', '-');
    }

    /**
     * Create the Gradle Zip task to gather test information
     *
     * @param project project to attach this task to
     * @param name    name of the task
     * @return a reference to the created task.
     */
    @NotNull
    public static Task createZipTask(Project project, String name) {
        final Task createCsvTask = project.getTasks().create(name + "CreateCsvFile", Task.class, t -> {
            // Parse all the junit results and write them to a csv file.
           t.doFirst(task -> {
                project.getLogger().warn("About to create CSV file and zip it");

                //  Read test xml files for tests and duration
                for (Path testResult : getTestXmlFiles(project.getRootDir().toPath())) {
                    try {
                        List<Tuple2<String, Long>> junitTests = fromJunitXml(new FileInputStream(testResult.toFile()));
                        for (Tuple2<String, Long> junitTest : junitTests) {
                            tests.addDuration(junitTest.getFirst(), junitTest.getSecond().longValue());
                        }
                    } catch (FileNotFoundException ignored) {
                    }
                }

                //  Write the test file to disk.
                try {
                    FileWriter writer = new FileWriter(new File(project.getRootDir(), ARTIFACT + ".csv"));
                    tests.write(writer);
                    LOG.warn("Written tests csv file with {} tests", tests.size());
                } catch (IOException ignored) {
                }
            });
        });

        return project.getTasks().create(name, Zip.class, z -> {
            z.getArchiveFileName().set(Artifactory.getFileName(ARTIFACT, EXTENSION, getGitBranch()));
            z.getDestinationDirectory().set(project.getRootDir());
            z.from(project.getRootDir(), task -> {
                task.include("**/" + ARTIFACT + ".csv");
            });
            z.dependsOn(createCsvTask);

            // ...base class method zips the CSV...

            z.doLast(task -> {
                //  We've now created the one csv file containing the tests and their mean durations,
                //  this task has zipped it, so we now just uploadd it.
                project.getLogger().warn("SAVING tests");
                project.getLogger().warn("Attempting to upload {}", z.getArchiveFileName().get());
                try (FileInputStream inputStream = new FileInputStream(new File(z.getArchiveFileName().get()))) {
                    if (!new TestDurationArtifacts().put(getGitBranch(), inputStream)) {
                        project.getLogger().warn("Could not upload zip of tests");
                    }
                    project.getLogger().warn("SAVED tests");
                } catch (Exception e) {
                    project.getLogger().warn("Problem trying to upload: ", z.getArchiveFileName().get(), e);
                }
            });
        });
    }

    private static List<Path> getTestXmlFiles(@NotNull final Path rootDir)  {
        List<Path> paths = new ArrayList<>();
        List<PathMatcher> matchers = new ArrayList<>();
        matchers.add(FileSystems.getDefault().getPathMatcher("glob:**/build/test-results-xml/**/*.xml"));
        matchers.add(FileSystems.getDefault().getPathMatcher("glob:**/build/test-results/**/*.xml"));
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
     *
     * @param inputStream stream containing zipped result file(s)
     * @return list of test name and durations
     */
    // TODO change the return type.
    @NotNull
    private static Tests fromZippedFiles(@NotNull final Tests tests, @NotNull final InputStream inputStream) {
        final List<Tuple2<String, Long>> results = new ArrayList<>();

        // We need this because ArchiveStream requires the `mark` functionality which is supported in buffered streams.
        final BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
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
                tests.addTests(Tests.read(reader));
            }
        } catch (ArchiveException | IOException e) {
            LOG.warn("Problem unzipping XML test results");
        }

        LOG.warn("Discovered {} tests", results.size());
        return tests;
    }

    /**
     * For a given stream, return the testcase names and durations.
     * <p>
     * NOTE:  the input stream will be closed by this method.
     *
     * @param inputStream an InputStream, closed once parsed
     * @return a list of test names and their durations in nanos.
     */
    @Nullable
    public static List<Tuple2<String, Long>> fromJunitXml(@NotNull final InputStream inputStream) {
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

            final BiFunction<NamedNodeMap, String, String> get = (a, k) -> a.getNamedItem(k) != null ? a.getNamedItem(k).getNodeValue() : "";
            for (int i = 0; i < nodeList.getLength(); i++) {
                final Node item = nodeList.item(i);
                final NamedNodeMap attributes = item.getAttributes();
                final String testName = get.apply(attributes,"name");
                final String testDuration = get.apply(attributes,"time");
                final String testClassName = get.apply(attributes,"classname");

                // If a test duration is zero, then don't add it.
                // When we look up the test later, we'll return the mean test time instead.
                if (!testName.isEmpty() && !testClassName.isEmpty() && !testDuration.isEmpty()) {
                    final long nanos = (long) (Double.parseDouble(testDuration) * 1000000.0);
                    results.add(new Tuple2<>(testClassName + "." + testName, nanos));
                }
            }
        } catch (ParserConfigurationException | IOException | XPathExpressionException | SAXException e) {
            // Same behaviour as the original CSV reader code.
            return null;
        }

        return results;
    }


    // The one and only set of tests information.  We load these at the start of a build, and update them and save them at the end.
    static Tests tests = new Tests();

    /**
     * A supplier of tests.
     * <p>
     * We get them from Artifactory and then parse the test xml files to get the duration.
     *
     * @param destDir destination that we will write the interim tests file to
     * @return a supplier of test results
     */
    @NotNull
    public static Supplier<Tests> getTestsSupplier(final File destDir) {
        return () -> {
            LOG.warn("LOADING tests from Artifactory");
            try {
                final TestDurationArtifacts testArtifacts = new TestDurationArtifacts();
                File testsFile = testArtifacts.get(getGitBranch(), destDir);

                if (testsFile == null) {
                    LOG.warn("Could not get tests from Artifactory for {}, trying {}", getGitBranch(), getTargetGitBranch());
                    testsFile = testArtifacts.get(getTargetGitBranch(), destDir);
                    if (tests == null) {
                        LOG.warn("Could not get any tests from Artifactory");
                        return tests;
                    }
                }
                try (FileInputStream inputStream = new FileInputStream(testsFile)) {
                    fromZippedFiles(tests, inputStream);
                    LOG.warn("Got {} tests from Artifactory", tests.size());
                    return tests;
                }
            } catch (Exception e) { // was IOException
                LOG.warn(e.toString());
                e.printStackTrace();
                LOG.warn("Could not get tests from Artifactory");
                return tests;
            }
        };
    }

    /**
     * Get tests for the specified tag in the outputStream
     *
     * @param theTag       tag for tests
     * @param outputStream stream of zipped xml files
     * @return false if we fail to get the tests
     */
    public boolean get(@NotNull final String theTag, @NotNull final OutputStream outputStream) {
        return artifactory.get(BASE_URL, theTag, "tests", "zip", outputStream);
    }

    /**
     * Get tests for the specified tag and save as a file
     *
     * @param theTag  tag for tests
     * @param destDir destination directory
     * @return the file
     */
    @Nullable
    private File get(@NotNull final String theTag, @NotNull final File destDir) {
        return artifactory.get(BASE_URL, theTag, "tests", "zip", destDir);
    }

    /**
     * Uplaod the supplied tests
     *
     * @param theTag      tag for tests
     * @param inputStream stream of zipped xml files.
     * @return true if we succeed
     */
    private boolean put(@NotNull final String theTag, @NotNull final InputStream inputStream) {
        return artifactory.put(BASE_URL, theTag, ARTIFACT, EXTENSION, inputStream);
    }
}

