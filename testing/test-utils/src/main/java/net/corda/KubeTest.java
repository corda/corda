package net.corda;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KubeTest {

    static class KubePodResult {

        private final Pod createdPod;
        private Thread outputThread;

        public KubePodResult(Pod createdPod, Thread outputThread) {
            this.createdPod = createdPod;
            this.outputThread = outputThread;
        }
    }

    private static Logger logger = LoggerFactory.getLogger(KubeTest.class);

    public static void main(String[] args) throws IOException, InterruptedException {

        String runId = new BigInteger(64, new Random()).toString(36).toLowerCase();

        final CountDownLatch closeLatch = new CountDownLatch(1);
        Config config = new ConfigBuilder().build();
        try (final KubernetesClient client = new DefaultKubernetesClient(config)) {

            String namespace = "thisisatest";
            client.apps().deployments().inNamespace(namespace).list().getItems().forEach(deploymentToDelete -> {
                client.resource(deploymentToDelete).delete();
            });

            client.pods().inNamespace(namespace).list().getItems().forEach(podToDelete -> {
                System.out.println("deleting: " + podToDelete.getMetadata().getName());
                client.resource(podToDelete).delete();
            });

            Namespace ns = new NamespaceBuilder().withNewMetadata().withName(namespace).addToLabels("this", "rocks").endMetadata().build();
            client.namespaces().createOrReplace(ns);

            List<KubePodResult> createdPods = IntStream.range(0, 4).mapToObj(i -> {
                String podName = "test" + runId + i;
                Pod podRequest = new PodBuilder().withNewMetadata().withName(podName).endMetadata()
                        .withNewSpec()
                        .addNewContainer()
                        .withImage("stefanotestingcr.azurecr.io/6f59dd3432ca")
                        .withCommand("bash")
                        .withArgs("-c", "cd /tmp/source && ./gradlew -PdockerFork=" + i + " -PdockerForks=4 node:test --info")
                        .withName("testing-hello-world" + i)
                        .withNewResources()
                        .addToRequests("cpu", new Quantity("2"))
                        .endResources()
                        .endContainer()
                        .withImagePullSecrets(new LocalObjectReference("regcred"))
                        .withRestartPolicy("Never")
                        .endSpec()
                        .build();

                System.out.println("created pod: " + podName);
                Pod createdPod = client.pods().inNamespace(namespace).create(podRequest);

                Thread outputThread = new Thread(() -> {
                    try {
                        client.pods().inNamespace(namespace).withName(podName).waitUntilReady(10, TimeUnit.MINUTES);
                        System.out.println("Pod: " + podName + " has started ");
                        LogWatch watch = client.pods().inNamespace(namespace).withName(podName).watchLog();
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(watch.getOutput()))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                System.out.println("Container" + i + ":   " + line);
                            }
                        } catch (IOException e) {
                            //nothing we can really do here
                        }
                    } catch (InterruptedException ignored) {
                        //we were interrupted whilst waiting for container
                    }

                });

                outputThread.start();
                return new KubePodResult(createdPod, outputThread);
            }).collect(Collectors.toList());

            System.out.println("Pods created, waiting for settle");
            Thread.sleep(30_000l);

            createdPods.forEach(pod -> {
                try {
                    pod.outputThread.join();
                } catch (InterruptedException e) {
                }
            });


            client.pods().inNamespace(namespace).list().getItems().forEach(podToDelete -> {
                System.out.println("deleting: " + podToDelete.getMetadata().getName());
                client.resource(podToDelete).delete();
            });

        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
            Throwable[] suppressed = e.getSuppressed();
            if (suppressed != null) {
                for (Throwable t : suppressed) {
                    logger.error(t.getMessage(), t);
                }
            }
        }
    }

    private static void configureWatcher(CountDownLatch closeLatch, KubernetesClient client, String namespace) {
        try (Watch watch = client.pods().inNamespace(namespace).withName("test").watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod resource) {
                System.out.println(action.toString() + " -> " + resource.getMetadata().getName());
            }

            @Override
            public void onClose(KubernetesClientException cause) {
                closeLatch.countDown();
            }
        })) {
            closeLatch.await(10, TimeUnit.SECONDS);
        } catch (KubernetesClientException | InterruptedException e) {
            logger.error("Could not watch resources", e);
        }
    }

}
