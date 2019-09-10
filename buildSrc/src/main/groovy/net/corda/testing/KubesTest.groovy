package net.corda.testing

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

    String namespace = "thisisatest"
    int k8sTimeout = 50 * 1_000
    int webSocketTimeout = k8sTimeout * 6
    int numberOfPods = 20
    int timeoutInMinutesForPodToStart = 60

    @TaskAction
    void runTestsOnKubes() {

        try {
            Class.forName("org.apache.commons.compress.archivers.tar.TarArchiveInputStream")
        } catch (ClassNotFoundException ignored) {
            throw new GradleException("Apache Commons compress has not be loaded, this can happen if running from within intellj - please select \"delegate to gradle\" for build and test actions")
        }

        def buildId = System.getProperty("buildId") ? System.getProperty("buildId") :
                (project.hasProperty("corda_revision") ? project.property("corda_revision").toString() : "0")

        def currentUser = System.getProperty("user.name") ? System.getProperty("user.name") : "UNKNOWN_USER"

        String stableRunId = new BigInteger(64, new Random(buildId.hashCode() + currentUser.hashCode())).toString(36).toLowerCase()
        String suffix = new BigInteger(64, new Random()).toString(36).toLowerCase()

        io.fabric8.kubernetes.client.Config config = new io.fabric8.kubernetes.client.ConfigBuilder()
                .withConnectionTimeout(k8sTimeout)
                .withRequestTimeout(k8sTimeout)
                .withRollingTimeout(k8sTimeout)
                .withWebsocketTimeout(webSocketTimeout)
                .withWebsocketPingInterval(webSocketTimeout)
                .build()

        final KubernetesClient client = new DefaultKubernetesClient(config)

        try {
            client.pods().inNamespace(namespace).list().getItems().forEach({ podToDelete ->
                if (podToDelete.getMetadata().name.contains(stableRunId)) {
                    project.logger.lifecycle("deleting: " + podToDelete.getMetadata().getName())
                    client.resource(podToDelete).delete()
                }
            })
        } catch (Exception ignored) {
            //it's possible that a pod is being deleted by the original build, this can lead to racey conditions
        }


        List<CompletableFuture<KubePodResult>> podCreationFutures = IntStream.range(0, numberOfPods).mapToObj({ i ->
            CompletableFuture.supplyAsync({
                File outputFile = Files.createTempFile("container", ".log").toFile()
                String podName = (taskToExecuteName + "-" + stableRunId + suffix + i).toLowerCase()
                Pod podRequest = buildPod(podName)
                project.logger.lifecycle("created pod: " + podName)
                Pod createdPod = client.pods().inNamespace(namespace).create(podRequest)
                Runtime.getRuntime().addShutdownHook({
                    println "Deleting pod: " + podName
                    client.pods().delete(createdPod)
                })
                CompletableFuture<Void> waiter = new CompletableFuture<Void>()
                KubePodResult result = new KubePodResult(createdPod, waiter, outputFile)
                startBuildAndLogging(client, namespace, numberOfPods, i, podName, printOutput, waiter, { int resultCode ->
                    println podName + " has completed with resultCode=$resultCode"
                    result.setResultCode(resultCode)
                }, outputFile)

                return result
            }, executorService)
        }).collect(Collectors.toList())

        def binaryFileFutures = podCreationFutures.collect { creationFuture ->
            return creationFuture.thenComposeAsync({ podResult ->
                return podResult.waiter.thenApply {
                    project.logger.lifecycle("Successfully terminated log streaming for " + podResult.createdPod.getMetadata().getName())
                    println "Gathering test results from ${podResult.createdPod.metadata.name}"
                    def binaryResults = downloadTestXmlFromPod(client, namespace, podResult.createdPod)
                    project.logger.lifecycle("deleting: " + podResult.createdPod.getMetadata().getName())
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
    }

    void startBuildAndLogging(KubernetesClient client,
                              String namespace,
                              int numberOfPods,
                              int podIdx,
                              String podName,
                              boolean printOutput,
                              CompletableFuture<Void> waiter,
                              Consumer<Integer> resultSetter,
                              File outputFileForContainer) {
        try {
            project.logger.lifecycle("Waiting for pod " + podName + " to start before executing build")
            client.pods().inNamespace(namespace).withName(podName).waitUntilReady(timeoutInMinutesForPodToStart, TimeUnit.MINUTES)
            project.logger.lifecycle("pod " + podName + " has started, executing build")
            Watch eventWatch = client.pods().inNamespace(namespace).withName(podName).watch(new Watcher<Pod>() {
                @Override
                void eventReceived(Watcher.Action action, Pod resource) {
                    project.logger.lifecycle("[StatusChange]  pod " + resource.getMetadata().getName() + " " + action.name())
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
                    project.logger.lifecycle("Build started on pod " + podName)
                }

                @Override
                void onFailure(Throwable t, Response response) {
                    project.logger.lifecycle("Received error from rom pod " + podName)
                    waiter.completeExceptionally(t)
                }

                @Override
                void onClose(int code, String reason) {
                    project.logger.lifecycle("Received onClose() from pod " + podName + " with returnCode=" + code)
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

            project.logger.lifecycle("Pod: " + podName + " has started ")

            Thread loggingThread = new Thread({ ->
                BufferedWriter out = null
                BufferedReader br = null
                try {
                    out = new BufferedWriter(new FileWriter(outputFileForContainer))
                    br = new BufferedReader(new InputStreamReader(stdOutIs))
                    String line
                    while ((line = br.readLine()) != null) {
                        def toWrite = ("${taskToExecuteName}/Container" + podIdx + ":   " + line).trim()
                        if (printOutput) {
                            project.logger.lifecycle(toWrite)
                        }
                        out.println(toWrite)
                    }
                } catch (IOException ignored) {
                }
                finally {
                    out?.close()
                    br?.close()
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
                .withPath("/tmp/gradle")
                .withType("DirectoryOrCreate")
                .endHostPath()
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
        return ["bash", "-c",
                "let x=1 ; while [ \${x} -ne 0 ] ; do echo \"Waiting for DNS\" ; curl services.gradle.org > /dev/null 2>&1 ; x=\$? ; sleep 1 ; done ; " +
                        "cd /tmp/source ; " +
                        "let y=1 ; while [ \${y} -ne 0 ] ; do echo \"Preparing build directory\" ; ./gradlew testClasses integrationTestClasses --parallel 2>&1 ; y=\$? ; sleep 1 ; done ;" +
                        "./gradlew -Dkubenetize -PdockerFork=" + podIdx + " -PdockerForks=" + numberOfPods + " $fullTaskToExecutePath --info 2>&1 ;" +
                        "let rs=\$? ; sleep 10 ; exit \${rs}"]
    }

    Collection<File> downloadTestXmlFromPod(KubernetesClient client, String namespace, Pod cp) {
        String resultsInContainerPath = "/tmp/source/build/test-reports"
        String binaryResultsFile = "results.bin"
        String podName = cp.getMetadata().getName()
        Path tempDir = new File(new File(project.getBuildDir(), "test-results-xml"), podName).toPath()

        if (!tempDir.toFile().exists()) {
            tempDir.toFile().mkdirs()
        }

        project.logger.lifecycle("saving to " + podName + " results to: " + tempDir.toAbsolutePath().toFile().getAbsolutePath())
        client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .dir(resultsInContainerPath)
                .copy(tempDir)

        return findFolderContainingBinaryResultsFile(new File(tempDir.toFile().getAbsolutePath()), binaryResultsFile)
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
