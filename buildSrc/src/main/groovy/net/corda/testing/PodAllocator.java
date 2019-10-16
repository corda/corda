package net.corda.testing;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.*;
import org.gradle.api.GradleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PodAllocator {

    private static final int CONNECTION_TIMEOUT = 60_1000;
    private final Logger logger;

    public PodAllocator(Logger logger) {
        this.logger = logger;
    }

    public PodAllocator() {
        this.logger = LoggerFactory.getLogger(PodAllocator.class);
    }

    public void allocatePods(Integer number, Integer coresPerPod, Integer memoryPerPod, String prefix) {

        Config config = new ConfigBuilder()
                .withConnectionTimeout(CONNECTION_TIMEOUT)
                .withRequestTimeout(CONNECTION_TIMEOUT)
                .withRollingTimeout(CONNECTION_TIMEOUT)
                .withWebsocketTimeout(CONNECTION_TIMEOUT)
                .withWebsocketPingInterval(CONNECTION_TIMEOUT)
                .build();

        KubernetesClient client = new DefaultKubernetesClient(config);

        List<Pod> podsToRequest = IntStream.range(0, number).mapToObj(i -> buildPod("pa-" + prefix + i, coresPerPod, memoryPerPod)).collect(Collectors.toList());

        synchronized (this) {
            podsToRequest.forEach(requestedPod -> {
                logger.info("Pre-allocating pod " + requestedPod.getMetadata().getName());
                client.pods().inNamespace(KubesTest.NAMESPACE).create(requestedPod);
            });
        }
    }

    public void tearDownPods(String prefix) {
        io.fabric8.kubernetes.client.Config config = new io.fabric8.kubernetes.client.ConfigBuilder()
                .withConnectionTimeout(CONNECTION_TIMEOUT)
                .withRequestTimeout(CONNECTION_TIMEOUT)
                .withRollingTimeout(CONNECTION_TIMEOUT)
                .withWebsocketTimeout(CONNECTION_TIMEOUT)
                .withWebsocketPingInterval(CONNECTION_TIMEOUT)
                .build();
        KubernetesClient client = new DefaultKubernetesClient(config);
        synchronized (this) {
            Stream<Pod> podsToDelete = client.pods().inNamespace(KubesTest.NAMESPACE).list()
                    .getItems()
                    .stream()
                    .sorted(Comparator.comparing(p -> p.getMetadata().getName()))
                    .filter(foundPod -> foundPod.getMetadata().getName().contains(prefix));

            List<CompletableFuture<Pod>> deleteFutures = podsToDelete.map(pod -> {
                CompletableFuture<Pod> result = new CompletableFuture<>();
                Watch watch = client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).watch(new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod resource) {
                        if (action == Action.DELETED) {
                            result.complete(resource);
                            if (logger instanceof org.gradle.api.logging.Logger) {
                                ((org.gradle.api.logging.Logger) logger).lifecycle("Successfully deleted pod " + pod.getMetadata().getName());
                            } else {
                                logger.info("Successfully deleted pod " + pod.getMetadata().getName());
                            }
                        }
                    }

                    @Override
                    public void onClose(KubernetesClientException cause) {
                        result.completeExceptionally(cause);
                    }
                });
                client.pods().delete(pod);
                return result;
            }).collect(Collectors.toList());

            AtomicLong fiveMinTimeout = new AtomicLong(TimeUnit.MINUTES.toMillis(5));

            Set<String> deletedPods = deleteFutures.stream().map(f -> {
                try {
                    if (fiveMinTimeout.get() > 0) {
                        long waitStartTime = System.currentTimeMillis();
                        Pod pod = f.get(fiveMinTimeout.get(), TimeUnit.MILLISECONDS);
                        //this might look odd, but we need to actually "decrement" the atomic long, which does not have subtract methods
                        fiveMinTimeout.addAndGet(System.currentTimeMillis() - waitStartTime);
                        return pod.getMetadata().getName();
                    } else {
                        return null;
                    }
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toSet());

            List<String> podsThatDidNotDelete = podsToDelete.map(pod -> pod.getMetadata().getName()).filter(deletedPods::contains).collect(Collectors.toList());

            if (!podsThatDidNotDelete.isEmpty()) {
                throw new GradleException("Failed to delete pods: " + podsThatDidNotDelete);
            }
        }
    }


    Pod buildPod(String podName, Integer coresPerPod, Integer memoryPerPod) {
        return new PodBuilder().withNewMetadata().withName(podName).endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withImage("busybox:latest")
                .withCommand("sh")
                .withArgs("-c", "sleep 600")
                .withName(podName)
                .withNewResources()
                .addToRequests("cpu", new Quantity(coresPerPod.toString()))
                .addToRequests("memory", new Quantity(memoryPerPod.toString() + "Gi"))
                .endResources()
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .build();
    }

}
