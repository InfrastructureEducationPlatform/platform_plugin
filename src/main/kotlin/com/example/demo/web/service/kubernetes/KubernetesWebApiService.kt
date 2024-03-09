package com.example.demo.web.service.kubernetes

import com.example.demo.utils.CommonUtils.log
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.*
import org.springframework.stereotype.Service
import java.io.File

@Service
class KubernetesWebApiService {
    fun createKubernetesWebServer() {
        // Kubernetes 클라이언트 생성
        val kubeConfigPath = "/Users/29078/.kube/config"

        // 클러스터 외부 구성인 kubeconfig 로드
        val config: Config = ConfigBuilder().withFile(File(kubeConfigPath)).build()
        val client: KubernetesClient = KubernetesClientBuilder().withConfig(config).build()

        try {
            // Pod 정의
            val pod: Pod = PodBuilder()
                .withNewMetadata()
                .withName("nginx-pod")
                .endMetadata()
                .withNewSpec()
                .addToContainers(ContainerBuilder()
                    .withName("nginx")
                    .withImage("nginx:latest")
                    .addNewPort()
                    .withContainerPort(80)
                    .endPort()
                    .build())
                .endSpec()
                .build()

            val createdPod: Pod = client.pods().inNamespace("default").resource(pod).create()

            log.info { "Nginx Pod created successfully." }
        } catch (e: Exception) {
            log.error { "Failed to create Nginx Pod: ${e.message}" }
        } finally {
            client.close()
        }
    }

}