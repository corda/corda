package net.corda.behave.service.containers

import io.kubernetes.client.ApiClient
import io.kubernetes.client.Attach
import io.kubernetes.client.Configuration
import io.kubernetes.client.util.Config
import net.corda.behave.service.ContainerService
import io.kubernetes.client.models.V1Pod
import io.kubernetes.client.models.V1PodList
import io.kubernetes.client.apis.CoreV1Api
import net.corda.nodeapi.internal.config.toConfig
import io.kubernetes.client.Attach.AttachResult
import io.kubernetes.client.ProtoClient
import io.kubernetes.client.proto.Meta
import io.kubernetes.client.proto.V1
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader
import io.kubernetes.client.proto.V1.Pod


class KubeContainerService(name: String, port: Int) : ContainerService(name, port) {

    val apiClient = Config.defaultClient()

    init {
        Configuration.setDefaultApiClient(apiClient)
    }

    private val api = CoreV1Api()

    override val internalPort = 1999

    override fun checkPrerequisites() {
        log.info("Checking prerequisites ...")

        // list available pods
        list()

        // Proto client
        val pc = ProtoClient(apiClient)

        log.info("Creating new namespace ...")
        val namespace = V1.Namespace.newBuilder().setMetadata(Meta.ObjectMeta.newBuilder().setName("test").build()).build()
        pc.create(namespace, "/api/v1/namespaces", "v1", "Namespace");

        log.info("Create pod ...")
//        pc.create(V1.Pod.newBuilder().build(),"/api/v1/namespaces", "v1", "Pod")

        val podBuilder = V1.Pod.newBuilder()
            podBuilder.metadataBuilder.setName("notary-healthcheck")
                    //.labelsMap.putIfAbsent("app","notary-healthcheck")
        val container = V1.Container.newBuilder()
                            .setName("notary-healthcheck")
                            .setImage("localhost:5000/r3/notary-healthcheck:3.0-snapshot")
                .setImagePullPolicy("Always")
//                .setEnv(0, V1.EnvVar.newBuilder().setName("RPC_PORT").setValue("10002"))
//                .setEnv(1, V1.EnvVar.newBuilder().setName("TARGET_HOST").setValue("corda-0.corda.default.svc.cluster.local"))
        podBuilder.specBuilder.addContainers(container)
        val builtPod = podBuilder.build()
        val status = pc.create(builtPod, "/api/v1/namespaces/test", "v1", "Namespace")
//        val status = pc.create(builtPod, "/api/v1/namespaces/test", "v1", "Namespace")
        println("status: $status")

        list()

        log.info("Deleting Pod ...")
        val ns2 = pc.delete<V1.Namespace>(V1.Namespace.newBuilder(), "/api/v1/namespaces/test")
//        val ns2 = pc.delete<V1.Pod>(V1.Pod.newBuilder(), "notary-healthcheck")
        System.out.println(ns2)
    }

    override fun startService(): Boolean {
        log.info("Starting service ...")
//        return super.startService()
        return true
    }

    override fun waitUntilStarted(): Boolean {
        log.info("Waiting for service to start ...")
//        return super.waitUntilStarted()
        return true
    }

    override fun verify(): Boolean {
        log.info("Verifying ...")
        return super.verify()
    }


    fun list() {
//        val list = api.listPodForAllNamespaces(null, "app=corda", "true", null, 60, false)
        val list = api.listPodForAllNamespaces(null, null, "true", null, 60, false)
        log.info("Available pods:")
        for (item in list.items) {
            println(item.metadata.name)
        }
    }

    fun attach(pod: V1Pod) {
        val attach = Attach()
//        val result = attach.attach("default", "nginx-4217019353-k5sn9", true)
        val result = attach.attach(pod, true)

        Thread(
                Runnable {
                    val `in` = BufferedReader(InputStreamReader(System.`in`))
                    val output = result.standardInputStream
                    try {
                        while (true) {
                            val line = `in`.readLine()
                            output.write(line.toByteArray())
                            output.write('\n'.toInt())
                            output.flush()
                        }
                    } catch (ex: IOException) {
                        ex.printStackTrace()
                    }
                })
                .start()
    }

    fun namespaces() {
        log.info("Namespaces ...")
        val pc = ProtoClient(apiClient)
        val list = pc.list<V1.PodList>(V1.PodList.newBuilder(), "/api/v1/namespace/default/pods")

        log.info("Listing pods for namespace:")
        if (list.`object` != null) {
            if (list.`object`.itemsCount > 0) {
                val p = list.`object`.getItems(0)
                println(p)
            }
        }

        log.info("Creating new namespace ...")
        val namespace = V1.Namespace.newBuilder().setMetadata(Meta.ObjectMeta.newBuilder().setName("test").build()).build()
        val  ns = pc.create(namespace, "/api/v1/namespaces", "v1", "Namespace");
        System.out.println(ns)
        if (ns.`object` != null) {
            val namespace =
                    ns.`object`
                            .toBuilder()
                            .setSpec(V1.NamespaceSpec.newBuilder().addFinalizers("test").build())
                            .build()
            // This is how you would update an object, but you can't actually
            // update namespaces, so this returns a 405
            log.info("Updating namespace ...")
            val ns = pc.update(namespace, "/api/v1/namespaces/test", "v1", "Namespace");
            System.out.println(ns.status);
        }

        log.info("Deleting namespace ...")
        val ns2 = pc.delete<V1.Namespace>(V1.Namespace.newBuilder(), "/api/v1/namespaces/test")
        System.out.println(ns2)
    }
}