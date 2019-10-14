package net.corda.testing;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.utils.Serialization;
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

    static final ExecutorService executorService = Executors.newCachedThreadPool();
    static final ExecutorService singleThreadedExecutor = Executors.newSingleThreadExecutor();

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

        String stableRunId = rnd64Base36(new Random(buildId.hashCode() + currentUser.hashCode() + taskToExecuteName.hashCode()), 64).toLowerCase();
        String suffix = rnd64Base36(new Random(), 64).toLowerCase();

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

        List<CompletableFuture<KubePodResult>> futures = IntStream.range(0, numberOfPods).mapToObj(i -> {
            String potentialPodName = (taskToExecuteName + "-" + stableRunId + suffix + i).toLowerCase();
            String podName = potentialPodName.substring(0, Math.min(potentialPodName.length(), 62));
            return runBuild(client, namespace, numberOfPods, i, podName, printOutput, 3);
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

    static String rnd64Base36(Random random, int bits) {
        return new BigInteger(64, random)
                .toString(bits)
                .toLowerCase();
    }

    @NotNull
    KubernetesClient getKubernetesClient() {
        io.fabric8.kubernetes.client.Config config = new io.fabric8.kubernetes.client.ConfigBuilder()
                .withConnectionTimeout(k8sTimeout)
                .withRequestTimeout(k8sTimeout)
                .withRollingTimeout(k8sTimeout)
                .withWebsocketTimeout(webSocketTimeout)
                .withWebsocketPingInterval(webSocketTimeout)
                .build();

        return new DefaultKubernetesClient(config);
    }

    PersistentVolumeClaim createPvc(KubernetesClient client, String name) {
        PersistentVolumeClaim pvc = client.persistentVolumeClaims()
                .inNamespace(namespace)
                .createNew()
                .editOrNewMetadata().withName(name).endMetadata()
                .editOrNewSpec()
                .withAccessModes("ReadWriteOnce")
                .editOrNewResources().addToRequests("storage", new Quantity("100Mi")).endResources()
                .endSpec()
                .done();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            getLogger().info("Deleting PVC: " + pvc.getMetadata().getName());
            client.persistentVolumeClaims().delete(pvc);
        }));
        return pvc;
    }

    CompletableFuture<KubePodResult> runBuild(KubernetesClient client,
                                              String namespace,
                                              int numberOfPods,
                                              int podIdx,
                                              String podName,
                                              boolean printOutput,
                                              int numberOfRetries) {

        CompletableFuture<KubePodResult> toReturn = new CompletableFuture<>();
        PersistentVolumeClaim pvc = createPvc(client, podName);
        executorService.submit(() -> {
            int tryCount = 0;
            Pod createdPod = null;
            while (tryCount < numberOfRetries) {
                try {
                    Pod podRequest = buildPod(podName, pvc);
                    getProject().getLogger().lifecycle("requesting pod: " + podName);
                    createdPod = client.pods().inNamespace(namespace).create(podRequest);
                    getProject().getLogger().lifecycle("scheduled pod: " + podName);
                    File outputFile = Files.createTempFile("container", ".log").toFile();
                    attachStatusListenerToPod(client, namespace, podName);
                    schedulePodForDeleteOnShutdown(podName, client, createdPod);
                    waitForPodToStart(podName, client, namespace);
                    PipedOutputStream stdOutOs = new PipedOutputStream();
                    PipedInputStream stdOutIs = new PipedInputStream(4096);
                    ByteArrayOutputStream errChannelStream = new ByteArrayOutputStream();
                    KubePodResult result = new KubePodResult(createdPod, outputFile);
                    CompletableFuture<KubePodResult> waiter = new CompletableFuture<>();
                    ExecListener execListener = buildExecListenerForPod(podName, errChannelStream, waiter, result);
                    stdOutIs.connect(stdOutOs);
                    String[] buildCommand = getBuildCommand(numberOfPods, podIdx);
                    ExecWatch execWatch = client.pods().inNamespace(namespace).withName(podName)
                            .writingOutput(stdOutOs)
                            .writingErrorChannel(errChannelStream)
                            .usingListener(execListener).exec(buildCommand);

                    startLogPumping(outputFile, stdOutIs, podIdx, printOutput);
                    KubePodResult execResult = waiter.join();
                    getLogger().lifecycle("build has ended on on pod " + podName + " (" + podIdx + "/" + numberOfPods + ")");
                    getLogger().lifecycle("Gathering test results from " + execResult.getCreatedPod().getMetadata().getName());
                    Collection<File> binaryResults = downloadTestXmlFromPod(client, namespace, execResult.getCreatedPod());
                    getLogger().lifecycle("deleting: " + execResult.getCreatedPod().getMetadata().getName());
                    client.resource(execResult.getCreatedPod()).delete();
                    result.setBinaryResults(binaryResults);
                    toReturn.complete(result);
                    break;
                } catch (Exception e) {
                    getLogger().error("Encountered error during testing cycle on pod " + podName + " (" + podIdx / numberOfPods + ")", e);
                    try {
                        if (createdPod != null) {
                            client.pods().delete(createdPod);
                            while (client.pods().inNamespace(namespace).list().getItems().stream().anyMatch(p -> Objects.equals(p.getMetadata().getName(), podName))) {
                                getLogger().warn("pod " + podName + " has not been deleted, waiting 1s");
                                Thread.sleep(1000);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    tryCount++;
                    getLogger().lifecycle("will retry " + podName + " another " + (numberOfRetries - tryCount) + " times");
                }
            }
            if (tryCount >= numberOfRetries) {
                toReturn.completeExceptionally(new RuntimeException("Failed to build in pod " + podName + " (" + podIdx + "/" + numberOfPods + ") within retry limit"));
            }
        });
        return toReturn;
    }


    Pod buildPod(String podName, PersistentVolumeClaim pvc) {
        return new PodBuilder().withNewMetadata().withName(podName).endMetadata()
                .withNewSpec()
                .addNewVolume()
                .withName("gradlecache")
                .withNewHostPath()
                .withPath("/tmp/gradle")
                .withType("DirectoryOrCreate")
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
                .addNewVolumeMount()
                .withName("gradlecache")
                .withMountPath("/tmp/gradle")
                .endVolumeMount()
                .endContainer()
                .withImagePullSecrets(new LocalObjectReference("regcred"))
                .withRestartPolicy("Never")
                .endSpec()
                .build();
    }

    void startLogPumping(File outputFile, InputStream stdOutIs, Integer podIdx, boolean printOutput) {
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
    }

    Watch attachStatusListenerToPod(KubernetesClient client, String namespace, String podName) {
        return client.pods().inNamespace(namespace).withName(podName).watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Watcher.Action action, Pod resource) {
                getProject().getLogger().lifecycle("[StatusChange]  pod " + resource.getMetadata().getName() + " " + action.name() + " (" + resource.getStatus().getPhase() + ")");
            }

            @Override
            public void onClose(KubernetesClientException cause) {
            }
        });
    }

    void waitForPodToStart(String podName, KubernetesClient client, String namespace) {
        getProject().getLogger().lifecycle("Waiting for pod " + podName + " to start before executing build");
        try {
            client.pods().inNamespace(namespace).withName(podName).waitUntilReady(timeoutInMinutesForPodToStart, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        getProject().getLogger().lifecycle("pod " + podName + " has started, executing build");
    }

    Collection<File> downloadTestXmlFromPod(KubernetesClient client, String namespace, Pod cp) {
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

    String[] getBuildCommand(int numberOfPods, int podIdx) {
        String shellScript = "let x=1 ; while [ ${x} -ne 0 ] ; do echo \"Waiting for DNS\" ; curl services.gradle.org > /dev/null 2>&1 ; x=$? ; sleep 1 ; done ; " + "cd /tmp/source ; " +
                "let y=1 ; while [ ${y} -ne 0 ] ; do echo \"Preparing build directory\" ; ./gradlew testClasses integrationTestClasses --parallel 2>&1 ; y=$? ; sleep 1 ; done ;" +
                "./gradlew -D" + ListTests.DISTRIBUTION_PROPERTY + "=" + distribution.name() + " -Dkubenetize -PdockerFork=" + podIdx + " -PdockerForks=" + numberOfPods + " " + fullTaskToExecutePath + " --info 2>&1 ;" +
                "let rs=$? ; sleep 10 ; exit ${rs}";
        return new String[]{"bash", "-c", shellScript};
    }

    List<File> findFolderContainingBinaryResultsFile(File start, String fileNameToFind) {
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

    void schedulePodForDeleteOnShutdown(String podName, KubernetesClient client, Pod createdPod) {
        getProject().getLogger().info("attaching shutdown hook for pod " + podName);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Deleting pod: " + podName);
            client.pods().delete(createdPod);
        }));
    }

    ExecListener buildExecListenerForPod(String podName, ByteArrayOutputStream errChannelStream, CompletableFuture<KubePodResult> waitingFuture, KubePodResult result) {

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
                    result.setResultCode(resultCode);
                    waitingFuture.complete(result);
                } catch (Exception e) {
                    waitingFuture.completeExceptionally(e);
                }
            }
        };
    }


}
