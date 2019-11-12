package net.corda.testing;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusCause;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import net.corda.testing.retry.Retry;
import okhttp3.Response;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KubesTest extends DefaultTask {

    static final String TEST_RUN_DIR = "/test-runs";
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    /**
     * Name of the k8s Secret object that holds the credentials to access the docker image registry
     */
    private static final String REGISTRY_CREDENTIALS_SECRET_NAME = "regcred";

    String dockerTag;
    String fullTaskToExecutePath;
    String taskToExecuteName;
    Boolean printOutput = false;

    Integer numberOfCoresPerFork = 4;
    Integer memoryGbPerFork = 6;
    public volatile List<File> testOutput = Collections.emptyList();
    public volatile List<KubePodResult> containerResults = Collections.emptyList();
    private List<String> remainingPods = Collections.synchronizedList(new ArrayList());

    public static String NAMESPACE = "thisisatest";
    int k8sTimeout = 50 * 1_000;
    int webSocketTimeout = k8sTimeout * 6;
    int numberOfPods = 5;
    int timeoutInMinutesForPodToStart = 60;

    DistributeTestsBy distribution = DistributeTestsBy.METHOD;
    PodLogLevel podLogLevel = PodLogLevel.INFO;

    @TaskAction
    public void runDistributedTests() {
        String buildId = System.getProperty("buildId", "0");
        String currentUser = System.getProperty("user.name", "UNKNOWN_USER");

        String stableRunId = rnd64Base36(new Random(buildId.hashCode() + currentUser.hashCode() + taskToExecuteName.hashCode()));
        String random = rnd64Base36(new Random());

        try (KubernetesClient client = getKubernetesClient()) {
            client.pods().inNamespace(NAMESPACE).list().getItems().forEach(podToDelete -> {
                if (podToDelete.getMetadata().getName().contains(stableRunId)) {
                    getProject().getLogger().lifecycle("deleting: " + podToDelete.getMetadata().getName());
                    client.resource(podToDelete).delete();
                }
            });
        } catch (Exception ignored) {
            //it's possible that a pod is being deleted by the original build, this can lead to racey conditions
        }

        List<Future<KubePodResult>> futures = IntStream.range(0, numberOfPods).mapToObj(i -> {
            String podName = generatePodName(stableRunId, random, i);
            return submitBuild(NAMESPACE, numberOfPods, i, podName, printOutput, 3);
        }).collect(Collectors.toList());

        this.testOutput = Collections.synchronizedList(futures.stream().map(it -> {
            try {
                return it.get().getBinaryResults();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).flatMap(Collection::stream).collect(Collectors.toList()));
        this.containerResults = futures.stream().map(it -> {
            try {
                return it.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    @NotNull
    private String generatePodName(String stableRunId, String random, int i) {
        int magicMaxLength = 63;
        String provisionalName = taskToExecuteName.toLowerCase() + "-" + stableRunId + "-" + random + "-" + i;
        //length = 100
        //100-63 = 37
        //subString(37, 100) -? string of 63 characters
        return provisionalName.substring(Math.max(provisionalName.length() - magicMaxLength, 0));
    }

    @NotNull
    private KubernetesClient getKubernetesClient() {
        io.fabric8.kubernetes.client.Config config = new io.fabric8.kubernetes.client.ConfigBuilder()
                .withConnectionTimeout(k8sTimeout)
                .withRequestTimeout(k8sTimeout)
                .withRollingTimeout(k8sTimeout)
                .withWebsocketTimeout(webSocketTimeout)
                .withWebsocketPingInterval(webSocketTimeout)
                .build();

        return new DefaultKubernetesClient(config);
    }

    private static String rnd64Base36(Random rnd) {
        return new BigInteger(64, rnd)
                .toString(36)
                .toLowerCase();
    }

    private CompletableFuture<KubePodResult> submitBuild(
            String namespace,
            int numberOfPods,
            int podIdx,
            String podName,
            boolean printOutput,
            int numberOfRetries
    ) {
        return CompletableFuture.supplyAsync(() -> {
            PersistentVolumeClaim pvc = createPvc(podName);
            return buildRunPodWithRetriesOrThrow(namespace, numberOfPods, podIdx, podName, printOutput, numberOfRetries, pvc);
        }, executorService);
    }

    private static void addShutdownHook(Runnable hook) {
        Runtime.getRuntime().addShutdownHook(new Thread(hook));
    }

    private PersistentVolumeClaim createPvc(String name) {
        PersistentVolumeClaim pvc;
        try (KubernetesClient client = getKubernetesClient()) {
            pvc = client.persistentVolumeClaims()
                    .inNamespace(NAMESPACE)
                    .createNew()
                    .editOrNewMetadata().withName(name).endMetadata()
                    .editOrNewSpec()
                    .withAccessModes("ReadWriteOnce")
                    .editOrNewResources().addToRequests("storage", new Quantity("100Mi")).endResources()
                    .withStorageClassName("testing-storage")
                    .endSpec()
                    .done();
        }

        addShutdownHook(() -> {
            try (KubernetesClient client = getKubernetesClient()) {
                System.out.println("Deleting PVC: " + pvc.getMetadata().getName());
                client.persistentVolumeClaims().delete(pvc);
            }
        });
        return pvc;
    }

    private KubePodResult buildRunPodWithRetriesOrThrow(
            String namespace,
            int numberOfPods,
            int podIdx,
            String podName,
            boolean printOutput,
            int numberOfRetries,
            PersistentVolumeClaim pvc) {
        addShutdownHook(() -> {
            System.out.println("deleting pod: " + podName);
            try (KubernetesClient client = getKubernetesClient()) {
                client.pods().inNamespace(namespace).withName(podName).delete();
            }
        });

        try {
            // pods might die, so we retry
            return Retry.fixed(numberOfRetries).run(() -> {
                // remove pod if exists
                Pod createdPod;
                try (KubernetesClient client = getKubernetesClient()) {
                    PodResource<Pod, DoneablePod> oldPod = client.pods().inNamespace(namespace).withName(podName);
                    if (oldPod.get() != null) {
                        getLogger().lifecycle("deleting pod: {}", podName);
                        oldPod.delete();
                        while (oldPod.get() != null) {
                            getLogger().info("waiting for pod {} to be removed", podName);
                            Thread.sleep(1000);
                        }
                    }
                    getProject().getLogger().lifecycle("creating pod: " + podName);
                    createdPod = client.pods().inNamespace(namespace).create(buildPodRequest(podName, pvc));
                    remainingPods.add(podName);
                    getProject().getLogger().lifecycle("scheduled pod: " + podName);
                }

                attachStatusListenerToPod(createdPod);
                waitForPodToStart(createdPod);

                PipedOutputStream stdOutOs = new PipedOutputStream();
                PipedInputStream stdOutIs = new PipedInputStream(4096);
                ByteArrayOutputStream errChannelStream = new ByteArrayOutputStream();

                CompletableFuture<Integer> waiter = new CompletableFuture<>();
                File podOutput = executeBuild(namespace, numberOfPods, podIdx, podName, printOutput, stdOutOs, stdOutIs, errChannelStream, waiter);


                int resCode = waiter.join();
                getProject().getLogger().lifecycle("build has ended on on pod " + podName + " (" + podIdx + "/" + numberOfPods + ") with result " + resCode + " , gathering results");
                Collection<File> binaryResults = downloadTestXmlFromPod(namespace, createdPod);
                getLogger().lifecycle("removing pod " + podName + " (" + podIdx + "/" + numberOfPods + ") after completed build");
                File podLogsDirectory = new File(getProject().getBuildDir(), "pod-logs");
                if (!podLogsDirectory.exists()) {
                    podLogsDirectory.mkdirs();
                }
                File logFileToArchive = new File(podLogsDirectory, podName + ".log");
                try (FileInputStream logIn = new FileInputStream(podOutput); FileOutputStream logOut = new FileOutputStream(logFileToArchive)) {
                    IOUtils.copy(logIn, logOut);
                }
                try (KubernetesClient client = getKubernetesClient()) {
                    client.pods().delete(createdPod);
                    client.persistentVolumeClaims().delete(pvc);
                    remainingPods.remove(podName);
                    getLogger().lifecycle("Remaining Pods:  ");
                    remainingPods.forEach(pod -> getLogger().lifecycle(pod));
                }
                return new KubePodResult(podIdx, resCode, podOutput, binaryResults);
            });
        } catch (Retry.RetryException e) {
            throw new RuntimeException("Failed to build in pod " + podName + " (" + podIdx + "/" + numberOfPods + ") in " + numberOfRetries + " attempts", e);
        }
    }

    @NotNull
    private File executeBuild(String namespace,
                              int numberOfPods,
                              int podIdx,
                              String podName,
                              boolean printOutput,
                              PipedOutputStream stdOutOs,
                              PipedInputStream stdOutIs,
                              ByteArrayOutputStream errChannelStream,
                              CompletableFuture<Integer> waiter) throws IOException {
        KubernetesClient client = getKubernetesClient();
        ExecListener execListener = buildExecListenerForPod(podName, errChannelStream, waiter, client);
        stdOutIs.connect(stdOutOs);

        String[] buildCommand = getBuildCommand(numberOfPods, podIdx);
        getProject().getLogger().quiet("About to execute " + Arrays.stream(buildCommand).reduce("", (s, s2) -> s + " " + s2) + " on pod " + podName);
        client.pods().inNamespace(namespace).withName(podName)
                .writingOutput(stdOutOs)
                .writingErrorChannel(errChannelStream)
                .usingListener(execListener)
                .exec(getBuildCommand(numberOfPods, podIdx));

        return startLogPumping(stdOutIs, podIdx, printOutput);
    }

    private Pod buildPodRequest(String podName, PersistentVolumeClaim pvc) {
        return new PodBuilder()
                .withNewMetadata().withName(podName).endMetadata()

                .withNewSpec()

                .addNewVolume()
                .withName("gradlecache")
                .withNewHostPath()
                .withType("DirectoryOrCreate")
                .withPath("/tmp/gradle")
                .endHostPath()
                .endVolume()
                .addNewVolume()
                .withName("testruns")
                .withNewPersistentVolumeClaim()
                .withClaimName(pvc.getMetadata().getName())
                .endPersistentVolumeClaim()
                .endVolume()
                .addNewContainer()
                .withImage(dockerTag)
                .withCommand("bash")
                .withArgs("-c", "sleep 3600")
                .addNewEnv()
                .withName("DRIVER_NODE_MEMORY")
                .withValue("1024m")
                .withName("DRIVER_WEB_MEMORY")
                .withValue("1024m")
                .endEnv()
                .withName(podName)
                .withNewResources()
                .addToRequests("cpu", new Quantity(numberOfCoresPerFork.toString()))
                .addToRequests("memory", new Quantity(memoryGbPerFork.toString() + "Gi"))
                .endResources()
                .addNewVolumeMount().withName("gradlecache").withMountPath("/tmp/gradle").endVolumeMount()
                .addNewVolumeMount().withName("testruns").withMountPath(TEST_RUN_DIR).endVolumeMount()
                .endContainer()

                .addNewImagePullSecret(REGISTRY_CREDENTIALS_SECRET_NAME)
                .withRestartPolicy("Never")

                .endSpec()
                .build();
    }

    private File startLogPumping(InputStream stdOutIs, int podIdx, boolean printOutput) throws IOException {
        File outputFile = Files.createTempFile("container", ".log").toFile();
        Thread loggingThread = new Thread(() -> {
            try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
                 BufferedReader br = new BufferedReader(new InputStreamReader(stdOutIs))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String toWrite = ("Container" + podIdx + ":   " + line).trim();
                    if (printOutput) {
                        getProject().getLogger().lifecycle(toWrite);
                    }
                    out.write(line);
                    out.newLine();
                }
            } catch (IOException ignored) {
            }
        });

        loggingThread.setDaemon(true);
        loggingThread.start();
        return outputFile;
    }

    private Watch attachStatusListenerToPod(Pod pod) {
        KubernetesClient client = getKubernetesClient();
        return client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Watcher.Action action, Pod resource) {
                getProject().getLogger().lifecycle("[StatusChange]  pod " + resource.getMetadata().getName() + " " + action.name() + " (" + resource.getStatus().getPhase() + ")");
            }

            @Override
            public void onClose(KubernetesClientException cause) {
                client.close();
            }
        });
    }

    private void waitForPodToStart(Pod pod) {
        try (KubernetesClient client = getKubernetesClient()) {
            getProject().getLogger().lifecycle("Waiting for pod " + pod.getMetadata().getName() + " to start before executing build");
            try {
                client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).waitUntilReady(timeoutInMinutesForPodToStart, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            getProject().getLogger().lifecycle("pod " + pod.getMetadata().getName() + " has started, executing build");
        }
    }

    private Collection<File> downloadTestXmlFromPod(String namespace, Pod cp) {
        String resultsInContainerPath = TEST_RUN_DIR + "/test-reports";
        String binaryResultsFile = "results.bin";
        String podName = cp.getMetadata().getName();
        Path tempDir = new File(new File(getProject().getBuildDir(), "test-results-xml"), podName).toPath();

        if (!tempDir.toFile().exists()) {
            tempDir.toFile().mkdirs();
        }
        getProject().getLogger().lifecycle("Saving " + podName + " results to: " + tempDir.toAbsolutePath().toFile().getAbsolutePath());
        try (KubernetesClient client = getKubernetesClient()) {
            client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .dir(resultsInContainerPath)
                    .copy(tempDir);
        }
        return findFolderContainingBinaryResultsFile(new File(tempDir.toFile().getAbsolutePath()), binaryResultsFile);
    }

    private String[] getBuildCommand(int numberOfPods, int podIdx) {
        final String gitBranch = " -Dgit.branch=" + Properties.getGitBranch();
        final String gitTargetBranch = " -Dgit.target.branch=" + Properties.getTargetGitBranch();
        final String artifactoryUsername = " -Dartifactory.username=" + Properties.getUsername() + " ";
        final String artifactoryPassword = " -Dartifactory.password=" + Properties.getPassword() + " ";

        String shellScript = "(let x=1 ; while [ ${x} -ne 0 ] ; do echo \"Waiting for DNS\" ; curl services.gradle.org > /dev/null 2>&1 ; x=$? ; sleep 1 ; done ) && "
                + " cd /tmp/source && " +
                "(let y=1 ; while [ ${y} -ne 0 ] ; do echo \"Preparing build directory\" ; ./gradlew testClasses integrationTestClasses --parallel 2>&1 ; y=$? ; sleep 1 ; done ) && " +
                "(./gradlew -D" + ListTests.DISTRIBUTION_PROPERTY + "=" + distribution.name() +
                gitBranch +
                gitTargetBranch +
                artifactoryUsername +
                artifactoryPassword +
                "-Dkubenetize -PdockerFork=" + podIdx + " -PdockerForks=" + numberOfPods + " " + fullTaskToExecutePath + " " + getLoggingLevel() + " 2>&1) ; " +
                "let rs=$? ; sleep 10 ; exit ${rs}";
        return new String[]{"bash", "-c", shellScript};
    }

    private String getLoggingLevel() {

        switch (podLogLevel) {
            case INFO:
                return " --info";
            case WARN:
                return " --warn";
            case QUIET:
                return " --quiet";
            case DEBUG:
                return " --debug";
            default:
                throw new IllegalArgumentException("LogLevel: " + podLogLevel + " is unknown");
        }

    }

    private List<File> findFolderContainingBinaryResultsFile(File start, String fileNameToFind) {
        Queue<File> filesToInspect = new LinkedList<>(Collections.singletonList(start));
        List<File> folders = new ArrayList<>();
        while (!filesToInspect.isEmpty()) {
            File fileToInspect = filesToInspect.poll();
            if (fileToInspect.getAbsolutePath().endsWith(fileNameToFind)) {
                folders.add(fileToInspect.getParentFile());
            }

            if (fileToInspect.isDirectory()) {
                filesToInspect.addAll(Arrays.stream(Optional.ofNullable(fileToInspect.listFiles()).orElse(new File[]{})).collect(Collectors.toList()));
            }
        }
        return folders;
    }

    private ExecListener buildExecListenerForPod(String podName, ByteArrayOutputStream errChannelStream, CompletableFuture<Integer> waitingFuture, KubernetesClient client) {

        return new ExecListener() {
            final Long start = System.currentTimeMillis();

            @Override
            public void onOpen(Response response) {
                getProject().getLogger().lifecycle("Build started on pod  " + podName);
            }

            @Override
            public void onFailure(Throwable t, Response response) {
                getProject().getLogger().lifecycle("Received error from pod  " + podName);
                waitingFuture.completeExceptionally(t);
            }

            @Override
            public void onClose(int code, String reason) {
                getProject().getLogger().lifecycle("Received onClose() from pod " + podName + " , build took: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
                try {
                    String errChannelContents = errChannelStream.toString();
                    Status status = Serialization.unmarshal(errChannelContents, Status.class);
                    Integer resultCode = Optional.ofNullable(status).map(Status::getDetails)
                            .map(StatusDetails::getCauses)
                            .flatMap(c -> c.stream().findFirst())
                            .map(StatusCause::getMessage)
                            .map(Integer::parseInt).orElse(0);
                    waitingFuture.complete(resultCode);
                } catch (Exception e) {
                    waitingFuture.completeExceptionally(e);
                } finally {
                    client.close();
                }
            }
        };
    }

}
