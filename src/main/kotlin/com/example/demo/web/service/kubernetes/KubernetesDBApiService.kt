package com.example.demo.web.service.kubernetes

import com.example.demo.utils.CommonUtils.log
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import java.io.File


class KubernetesDBApiService {
    fun createKubernetesDB() {
        val namespace = "default"
        val serviceName = "postgresql"
        val port = 5432
        val kubeConfigPath = "/Users/29078/.kube/config"

        // 클러스터 외부 구성인 kubeconfig 로드
        val config: Config = ConfigBuilder().withFile(File(kubeConfigPath)).build()
        val client: KubernetesClient = KubernetesClientBuilder().withConfig(config).build()

        try {
            // Create PostgreSQL Service
            val service = ServiceBuilder()
                .withNewMetadata()
                .withName(serviceName)
                .endMetadata()
                .withNewSpec()
                .addToSelector("app", "postgresql")
                .addNewPort()
                .withProtocol("TCP")
                .withPort(port)
                .withNewTargetPort(port)
                .endPort()
                .endSpec()
                .build()

            client.services().inNamespace(namespace).resource(service).create()

            log.info {"PostgreSQL Service created successfully."}
        } catch (e: Exception) {
            log.error {"Failed to create PostgreSQL Service: ${e.message}"}
        } finally {
            client.close()
        }
    }
}