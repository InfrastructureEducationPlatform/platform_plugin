package com.example.demo.web.service.kubernetes

import com.example.demo.utils.CommonUtils.log
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.springframework.stereotype.Service
import java.io.File

@Service
class KubernetesLoadBalancerService {
    fun createLoadBalancer() {
        val namespace = "default"
        val serviceName = "my-load-balancer-service"
        val port = 8080
        val kubeConfigPath = "/Users/29078/.kube/config"

        // 클러스터 외부 구성인 kubeconfig 로드
        val config: Config = ConfigBuilder().withFile(File(kubeConfigPath)).build()
        val client: KubernetesClient = KubernetesClientBuilder().withConfig(config).build()

        try {
            // Create LoadBalancer Service
            val service = ServiceBuilder()
                .withNewMetadata()
                .withName(serviceName)
                .endMetadata()
                .withNewSpec()
                .withType("LoadBalancer")
                .addNewPort()
                .withProtocol("TCP")
                .withPort(port)
                .withNewTargetPort(port)
                .endPort()
                .addToSelector("app", "my-app")
                .endSpec()
                .build()

            client.services().inNamespace(namespace).resource(service).create()
            val serviceURL = client.services().inNamespace(namespace).withName(service.metadata.name)
                .getURL("80")
            log.info {"Service URL $serviceURL"}

            log.info {"LoadBalancer Service created successfully."}
        } catch (e: Exception) {
            log.error {"Failed to create LoadBalancer Service: ${e.message}"}
        } finally {
            client.close()
        }
    }
}