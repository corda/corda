package net.corda.testing;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import net.corda.testing.retry.Retry;
import okhttp3.Response;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
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

    String namespace = "thisisatest";
    int k8sTimeout = 50 * 1_000;
    int webSocketTimeout = k8sTimeout * 6;
    int numberOfPods = 20;
    int timeoutInMinutesForPodToStart = 60;

    Distribution distribution = Distribution.METHOD;

    @TaskAction
    public void runDistributedTests() {
        String buildId = System.getProperty("buildId", "0");
        String currentUser = System.getProperty("user.name", "UNKNOWN_USER");

        String stableRunId = rnd64Base36(new Random(buildId.hashCode() + currentUser.hashCode() + taskToExecuteName.hashCode()));
        String random = rnd64Base36(new Random());

        final KubernetesClient client = getKubernetesClient();

        try {
            client.pods().inNamespace(namespace).list().getItems().forEach(podToDelete -> {
                if (podToDelete.getMetadata().getName().contains(stableRunId)) {
                    getProject().getLogger().lifecycle("deleting: " + podToDelete.getMetadata().getName());
                    client.resource(podToDelete).delete();
                }
            });
        } catch (Exception ignored) {
            //it's possible that a pod is being deleted by the original build, this can lead to racey conditions
        }

        List<Future<KubePodResult>> futures = IntStream.range(0, numberOfPods).mapToObj(i -> {
            String podName = taskToExecuteName.toLowerCase() + "-" + stableRunId + "-" + random + "-" + i;
            return submitBuild(client, namespace, numberOfPods, i, podName, printOutput, 3);
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

    private Future<KubePodResult> submitBuild(
            KubernetesClient client,
            String namespace,
            int numberOfPods,
            int podIdx,
            String podName,
            boolean printOutput,
            int numberOfRetries
    ) {
        return executorService.submit(() -> buildRunPodWithRetriesOrThrow(client, namespace, numberOfPods, podIdx, podName, printOutput, numberOfRetries));
    }

    private static void addShutdownHook(Runnable hook) {
        Runtime.getRuntime().addShutdownHook(new Thread(hook));
    }

    private PersistentVolumeClaim createPvc(KubernetesClient client, String name) {
        PersistentVolumeClaim pvc = client.persistentVolumeClaims()
                .inNamespace(namespace)
                .createNew()

                .editOrNewMetadata().withName(name).endMetadata()

                .editOrNewSpec()
                .withAccessModes("ReadWriteOnce")
                .editOrNewResources().addToRequests("storage", new Quantity("100Mi")).endResources()
                .endSpec()

                .done();

        addShutdownHook(() -> {
            System.out.println("Deleing PVC: " + pvc.getMetadata().getName());
            client.persistentVolumeClaims().delete(pvc);
        });
        return pvc;
    }

    private KubePodResult buildRunPodWithRetriesOrThrow(
            KubernetesClient client,
            String namespace,
            int numberOfPods,
            int podIdx,
            String podName,
            boolean printOutput,
            int numberOfRetries
    ) {
        addShutdownHook(() -> {
            System.out.println("deleting pod: " + podName);
            client.pods().inNamespace(namespace).withName(podName).delete();
        });

        try {
            // pods might die, so we retry
            return Retry.fixed(numberOfRetries).run(() -> {
                // remove pod if exists
                PodResource<Pod, DoneablePod> oldPod = client.pods().inNamespace(namespace).withName(podName);
                if (oldPod.get() != null) {
                    getLogger().lifecycle("deleting pod: {}", podName);
                    oldPod.delete();
                    while (oldPod.get() != null) {
                        getLogger().info("waiting for pod {} to be removed", podName);
                        Thread.sleep(1000);
                    }
                }

                // recreate and run
                getProject().getLogger().lifecycle("creating pod: " + podName);
                Pod createdPod = client.pods().inNamespace(namespace).create(buildPodRequest(podName));
                getProject().getLogger().lifecycle("scheduled pod: " + podName);

                attachStatusListenerToPod(client, createdPod);
                waitForPodToStart(client, createdPod);

                PipedOutputStream stdOutOs = new PipedOutputStream();
                PipedInputStream stdOutIs = new PipedInputStream(4096);
                ByteArrayOutputStream errChannelStream = new ByteArrayOutputStream();

                CompletableFuture<Integer> waiter = new CompletableFuture<>();
                ExecListener execListener = buildExecListenerForPod(podName, errChannelStream, waiter);
                stdOutIs.connect(stdOutOs);
                client.pods().inNamespace(namespace).withName(podName)
                        .writingOutput(stdOutOs)
                        .writingErrorChannel(errChannelStream)
                        .usingListener(execListener)
                        .exec(getBuildCommand(numberOfPods, podIdx));

                File podOutput = startLogPumping(stdOutIs, podIdx, printOutput);
                int resCode = waiter.join();
                getProject().getLogger().lifecycle("build has ended on on pod " + podName + " (" + podIdx + "/" + numberOfPods + "), gathering results");
                Collection<File> binaryResults = downloadTestXmlFromPod(client, namespace, createdPod);
                return new KubePodResult(resCode, podOutput, binaryResults);
            });
        } catch (Retry.RetryException e) {
            throw new RuntimeException("Failed to build in pod " + podName + " (" + podIdx + "/" + numberOfPods + ") in " + numberOfRetries + " attempts", e);
        }
    }

    private Pod buildPodRequest(String podName) {
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
                .withNewHostPath()
                .withType("DirectoryOrCreate")
                .withPath("/tmp/testruns")
                .endHostPath()
                .endVolume()


//                .addNewVolume()
//                .withName("testruns")
//                .withNewPersistentVolumeClaim()
//                .withClaimName(pvc.getMetadata().getName())
//                .endPersistentVolumeClaim()
//                .endVolume()

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

    private Watch attachStatusListenerToPod(KubernetesClient client, Pod pod) {
        return client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Watcher.Action action, Pod resource) {
                getProject().getLogger().lifecycle("[StatusChange]  pod " + resource.getMetadata().getName() + " " + action.name() + " (" + resource.getStatus().getPhase() + ")");
            }

            @Override
            public void onClose(KubernetesClientException cause) {
            }
        });
    }

    private void waitForPodToStart(KubernetesClient client, Pod pod) {
        getProject().getLogger().lifecycle("Waiting for pod " + pod.getMetadata().getName() + " to start before executing build");
        try {
            client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).waitUntilReady(timeoutInMinutesForPodToStart, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        getProject().getLogger().lifecycle("pod " + pod.getMetadata().getName() + " has started, executing build");
    }

    private Collection<File> downloadTestXmlFromPod(KubernetesClient client, String namespace, Pod cp) {
        String resultsInContainerPath = "/tmp/source/build/test-reports";
        String binaryResultsFile = "results.bin";
        String podName = cp.getMetadata().getName();
        Path tempDir = new File(new File(getProject().getBuildDir(), "test-results-xml"), podName).toPath();

        if (!tempDir.toFile().exists()) {
            tempDir.toFile().mkdirs();
        }

        getProject().getLogger().lifecycle("Saving " + podName + " results to: " + tempDir.toAbsolutePath().toFile().getAbsolutePath());
        client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .dir(resultsInContainerPath)
                .copy(tempDir);

        return findFolderContainingBinaryResultsFile(new File(tempDir.toFile().getAbsolutePath()), binaryResultsFile);
    }

    private String[] getBuildCommand(int numberOfPods, int podIdx) {
        final String gitBranch = " -Dgit.branch=" +TestArtifacts.getGitBranch();
        final String artifactoryUsername = " -Dartifactory.username=" + Artifactory.getUsername() +" ";
        final String artifactoryPassword = " -Dartifactory.password=" + Artifactory.getPassword() + " ";

        String shellScript = "let x=1 ; while [ ${x} -ne 0 ] ; do echo \"Waiting for DNS\" ; curl services.gradle.org > /dev/null 2>&1 ; x=$? ; sleep 1 ; done ; " + "cd /tmp/source ; " +
                "let y=1 ; while [ ${y} -ne 0 ] ; do echo \"Preparing build directory\" ; ./gradlew testClasses integrationTestClasses --parallel 2>&1 ; y=$? ; sleep 1 ; done ;" +
                "./gradlew -D" + ListTests.DISTRIBUTION_PROPERTY + "=" + distribution.name() +
                gitBranch +
                artifactoryUsername +
                artifactoryPassword +
                " -Dkubenetize -PdockerFork=" + podIdx + " -PdockerForks=" + numberOfPods + " " + fullTaskToExecutePath + " --info 2>&1 ;" +
                "let rs=$? ; sleep 10 ; exit ${rs}";
        return new String[]{"bash", "-c", shellScript};
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
                filesToInspect.addAll(Arrays.stream(fileToInspect.listFiles()).collect(Collectors.toList()));
            }
        }
        return folders;
    }

    private ExecListener buildExecListenerForPod(String podName, ByteArrayOutputStream errChannelStream, CompletableFuture<Integer> waitingFuture) {

        return new ExecListener() {
            final Long start = System.currentTimeMillis();

            @Override
            public void onOpen(Response response) {
                getProject().getLogger().lifecycle("Build started on pod  " + podName);
            }

            @Override
            public void onFailure(Throwable t, Response response) {
                getProject().getLogger().lifecycle("Received error from rom pod  " + podName);
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
                }
            }
        };
    }

}
