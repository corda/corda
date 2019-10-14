package net.corda.testing;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PodAllocator {

    private static final int CONNECTION_TIMEOUT = 60_1000;
    private volatile List<Pod> preAllocatedPods = Collections.emptyList();
    private static final Logger logger = LoggerFactory.getLogger(PodAllocator.class);

    public void allocatePods(Integer number, Integer coresPerPod, Integer memoryPerPod) {

        Config config = new ConfigBuilder()
                .withConnectionTimeout(CONNECTION_TIMEOUT)
                .withRequestTimeout(CONNECTION_TIMEOUT)
                .withRollingTimeout(CONNECTION_TIMEOUT)
                .withWebsocketTimeout(CONNECTION_TIMEOUT)
                .withWebsocketPingInterval(CONNECTION_TIMEOUT)
                .build();

        KubernetesClient client = new DefaultKubernetesClient(config);

        String preAllocatorPrefix = new BigInteger(256, new Random()).toString(36).substring(0, 8);
        List<Pod> podsToRequest = IntStream.range(0, number).mapToObj(i -> buildPod("pa-" + preAllocatorPrefix + i, coresPerPod, memoryPerPod)).collect(Collectors.toList());

        synchronized (this) {
            this.preAllocatedPods = podsToRequest.stream().map(requestedPod -> {
                logger.info("Pre-allocating pod " + requestedPod.getMetadata().getName());
                Pod createdPod = client.pods().inNamespace(KubesTest.NAMESPACE).create(requestedPod);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> client.pods().delete(createdPod)));
                return createdPod;
            }).collect(Collectors.toList());
        }
    }

    public void tearDownPods() {

        io.fabric8.kubernetes.client.Config config = new io.fabric8.kubernetes.client.ConfigBuilder()
                .withConnectionTimeout(CONNECTION_TIMEOUT)
                .withRequestTimeout(CONNECTION_TIMEOUT)
                .withRollingTimeout(CONNECTION_TIMEOUT)
                .withWebsocketTimeout(CONNECTION_TIMEOUT)
                .withWebsocketPingInterval(CONNECTION_TIMEOUT)
                .build();

        KubernetesClient client = new DefaultKubernetesClient(config);
        synchronized (this) {
            this.preAllocatedPods.forEach(pod -> {
                logger.info("deleting " + pod.getMetadata().getName());
                client.pods().delete(pod);
            });
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
