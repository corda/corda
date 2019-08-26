package com.stefano.testing

import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.*
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.utils.Serialization
import okhttp3.Response
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.IntStream

class KubesTest extends DefaultTask {

    static final ExecutorService executorService = Executors.newCachedThreadPool()
    static final ExecutorService singleThreadedExecutor = Executors.newSingleThreadExecutor()

    String dockerTag
    String fullTaskToExecutePath
    String taskToExecuteName
    Boolean printOutput = false
    public volatile List<File> testOutput = Collections.emptyList()
    public volatile List<KubePodResult> containerResults = Collections.emptyList()


    @TaskAction
    void runTestsOnKubes() {
        String runId = new BigInteger(64, new Random()).toString(36).toLowerCase()

        int k8sTimeout = 50 * 1_000
        io.fabric8.kubernetes.client.Config config = new io.fabric8.kubernetes.client.ConfigBuilder()
                .withConnectionTimeout(k8sTimeout)
                .withRequestTimeout(k8sTimeout)
                .withRollingTimeout(k8sTimeout)
                .withWebsocketTimeout(k8sTimeout * 6)
                .withWebsocketPingInterval(k8sTimeout * 6)
                .build()

        final KubernetesClient client = new DefaultKubernetesClient(config)

        String namespace = "thisisatest"

        client.pods().inNamespace(namespace).list().getItems().forEach({ podToDelete ->
            System.out.println("deleting: " + podToDelete.getMetadata().getName())
            client.resource(podToDelete).delete()
        })

        Namespace ns = new NamespaceBuilder().withNewMetadata().withName(namespace).addToLabels("this", "rocks").endMetadata().build()
        client.namespaces().createOrReplace(ns)

        int numberOfNodes = 20
        List<CompletableFuture<KubePodResult>> podCreationFutures = IntStream.range(0, numberOfNodes).mapToObj({ i ->

            CompletableFuture.supplyAsync({
                File outputFile = Files.createTempFile("container", ".log").toFile()

                String podName = (taskToExecuteName + "-" + runId + i).toLowerCase()
                Pod podRequest = buildPod(podName)
                System.out.println("created pod: " + podName)
                Pod createdPod = client.pods().inNamespace(namespace).create(podRequest)


                AtomicReference<Throwable> errorHolder = new AtomicReference<>()
                CompletableFuture<Void> waiter = new CompletableFuture<Void>()
                KubePodResult result = new KubePodResult(createdPod, errorHolder, waiter, outputFile)
                startBuildAndLogging(client, namespace, numberOfNodes, i, podName, printOutput, errorHolder, waiter, { int resultCode ->
                    println podName + " has completed with resultCode=$resultCode"
                    result.setResultCode(resultCode)
                }, outputFile)

                return result
            }, executorService)


        }).collect(Collectors.toList())

        def binaryFileFutures = podCreationFutures.collect { creationFuture ->
            return creationFuture.thenComposeAsync({ podResult ->
                return podResult.waiter.thenApply {
                    System.out.println("Successfully terminated log streaming for " + podResult.createdPod.getMetadata().getName())
                    println "Gathering test results from ${podResult.createdPod.metadata.name}"
                    def binaryResults = downloadTestXmlFromPod(client, namespace, podResult.createdPod)
                    System.out.println("deleting: " + podResult.createdPod.getMetadata().getName())
                    client.resource(podResult.createdPod).delete()
                    return binaryResults
                }
            }, singleThreadedExecutor)
        }

        def allFilesDownloadedFuture = CompletableFuture.allOf(*binaryFileFutures.toArray(new CompletableFuture[0])).thenApply {
            def allBinaryFiles = binaryFileFutures.collect { future ->
                Collection<File> binaryFiles = future.get()
                return binaryFiles
            }.flatten()
            this.testOutput = Collections.synchronizedList(allBinaryFiles)
            return allBinaryFiles
        }

        allFilesDownloadedFuture.get()
        this.containerResults = podCreationFutures.collect { it -> it.get() }
        println ""
    }

    void startBuildAndLogging(KubernetesClient client,
                              String namespace,
                              int numberOfPods,
                              int podIdx,
                              String podName,
                              boolean printOutput,
                              AtomicReference<Throwable> errorHolder,
                              CompletableFuture<Void> waiter,
                              Consumer<Integer> resultSetter,
                              File outputFileForContainer) {
        try {

            System.out.println("Waiting for pod " + podName + " to start before executing build")
            client.pods().inNamespace(namespace).withName(podName).waitUntilReady(60, TimeUnit.MINUTES)
            System.out.println("pod " + podName + " has started, executing build")
            Watch eventWatch = client.pods().inNamespace(namespace).withName(podName).watch(new Watcher<Pod>() {
                @Override
                void eventReceived(Watcher.Action action, Pod resource) {
                    System.out.println("[StatusChange]  pod " + resource.getMetadata().getName() + " " + action.name())
                }

                @Override
                void onClose(KubernetesClientException cause) {
                }
            })


            def stdOutOs = new PipedOutputStream()
            def stdOutIs = new PipedInputStream(4096)
            ByteArrayOutputStream errChannelStream = new ByteArrayOutputStream();

            def terminatingListener = new ExecListener() {

                @Override
                void onOpen(Response response) {
                    System.out.println("Build started on pod " + podName)
                }

                @Override
                void onFailure(Throwable t, Response response) {
                    System.out.println("Received error from rom pod " + podName)
                    waiter.completeExceptionally(t)
                }

                @Override
                void onClose(int code, String reason) {
                    System.out.println("Received onClose() from pod " + podName + " with returnCode=" + code)
                    try {
                        def errChannelContents = errChannelStream.toString()
                        println errChannelContents
                        Status status = Serialization.unmarshal(errChannelContents, Status.class);
                        resultSetter.accept(status.details?.causes?.first()?.message?.toInteger() ? status.details?.causes?.first()?.message?.toInteger() : 0)
                        waiter.complete()
                    } catch (Exception e) {
                        waiter.completeExceptionally(e)
                    }
                }
            }

            stdOutIs.connect(stdOutOs)

            ExecWatch execWatch = client.pods().inNamespace(namespace).withName(podName)
                    .writingOutput(stdOutOs)
                    .writingErrorChannel(errChannelStream)
                    .usingListener(terminatingListener).exec(getBuildCommand(numberOfPods, podIdx))

            System.out.println("Pod: " + podName + " has started ")

            Thread loggingThread = new Thread({ ->
                BufferedWriter out = new BufferedWriter(new FileWriter(outputFileForContainer))
                BufferedReader br = new BufferedReader(new InputStreamReader(stdOutIs))
                try {
                    String line
                    while ((line = br.readLine()) != null) {
                        def toWrite = ("${taskToExecuteName}/Container" + podIdx + ":   " + line).trim()
                        if (printOutput) {
                            System.out.println(toWrite)
                        }
                        out.println(toWrite)
                    }
                } catch (IOException ignored) {
                }
                finally {
                    out.close()
                    br.close()
                }
            })

            loggingThread.setDaemon(true)
            loggingThread.start()
        } catch (InterruptedException ignored) {
            throw new GradleException("Could not get slot on cluster within timeout")
        }
    }

    Pod buildPod(String podName) {
        return new PodBuilder().withNewMetadata().withName(podName).endMetadata()
                .withNewSpec()
                .addNewVolume()
                .withName("gradlecache")
                .withNewHostPath()
                .withPath("/gradle")
                .withType("DirectoryOrCreate")
                .endHostPath()
                .endVolume()
                .addNewContainer()
                .withImage(dockerTag)
                .withCommand("bash")
        //max container life time is 30min
                .withArgs("-c", "sleep 1800")
                .addNewEnv()
                .withName("DRIVER_NODE_MEMORY")
                .withValue("1024m")
                .withName("DRIVER_WEB_MEMORY")
                .withValue("1024m")
                .endEnv()
                .withName(podName)
                .withNewResources()
                .addToRequests("cpu", new Quantity("2"))
                .addToRequests("memory", new Quantity("6Gi"))
                .endResources()
                .addNewVolumeMount()
                .withName("gradlecache")
                .withMountPath("/tmp/gradle")
                .endVolumeMount()
                .endContainer()
                .withImagePullSecrets(new LocalObjectReference("regcred"))
                .withRestartPolicy("Never")
                .endSpec()
                .build()
    }

    String[] getBuildCommand(int numberOfPods, int podIdx) {
        return ["bash", "-c", "cd /tmp/source && ./gradlew -PdockerFork=" + podIdx + " -PdockerForks=" + numberOfPods + " $fullTaskToExecutePath --info 2>&1 ; sleep 10"]
    }

    Collection<File> downloadTestXmlFromPod(KubernetesClient client, String namespace, Pod cp) {
        String resultsInContainerPath = "/tmp/source/build/test-reports"
        String binaryResultsFile = "results.bin"
        String podName = cp.getMetadata().getName()
        Path tempDir = new File(new File(project.getBuildDir(), "test-results-xml"), podName).toPath()

        if (!tempDir.toFile().exists()) {
            tempDir.toFile().mkdirs()
        }

        System.out.println("saving to " + podName + " results to: " + tempDir.toAbsolutePath().toFile().getAbsolutePath())
        boolean copiedResult = false
        try {
            client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .dir(resultsInContainerPath)
                    .copy(tempDir)
            copiedResult = true
        } catch (Exception ignored) {
            throw ignored
        }

        if (copiedResult) {
            return findFolderContainingBinaryResultsFile(new File(tempDir.toFile().getAbsolutePath()), binaryResultsFile)
        } else {
            return Collections.emptyList()
        }
    }

    List<File> findFolderContainingBinaryResultsFile(File start, String fileNameToFind) {
        Queue<File> filesToInspect = new LinkedList<>(Collections.singletonList(start))
        List<File> folders = new ArrayList<>()
        while (!filesToInspect.isEmpty()) {
            File fileToInspect = filesToInspect.poll()
            if (fileToInspect.getAbsolutePath().endsWith(fileNameToFind)) {
                folders.add(fileToInspect.parentFile)
            }

            if (fileToInspect.isDirectory()) {
                filesToInspect.addAll(Arrays.stream(fileToInspect.listFiles()).collect(Collectors.toList()))
            }
        }
        return folders
    }

}
