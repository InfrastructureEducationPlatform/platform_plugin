package com.example.demo.web.service.kubernetes

import com.example.demo.utils.CommonUtils.log
import com.example.demo.web.dto.Block
import com.example.demo.web.dto.BlockOutput
import com.example.demo.web.dto.VirtualMachineOutput
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.springframework.stereotype.Service
import java.io.File

@Service
class KubernetesVMApiService {
    fun createKubernetesVM(block: Block): BlockOutput {
        val kubeConfigPath = "/Users/29078/.kube/config"

        // 클러스터 외부 구성인 kubeconfig 로드
        val config: Config = ConfigBuilder().withFile(File(kubeConfigPath)).build()
        val client: KubernetesClient = KubernetesClientBuilder().withConfig(config).build()

        client.use { client ->
            // 가상 머신의 Pod 설정
            val pod: Pod = PodBuilder()
                .withNewMetadata()
                .withName(block.name)
                .endMetadata()
                .withNewSpec()
                .addToContainers(ContainerBuilder()
                    .withName("test-container")
                    .withImage("ubuntu:latest")
                    .withCommand("sleep", "infinity")
                    .addNewPort()
                    .withContainerPort(80)
                    .endPort()
                    .build())
                .endSpec()
                .build()

            // 가상 머신 생성
            val createdPod: Pod = client.pods().inNamespace("default").resource(pod).create()

            log.info { "Virtual Machine created: ${createdPod.metadata?.name}" }

            // 서비스 생성
            val serviceName = "vm-service-${block.name}"
            val service = ServiceBuilder()
                .withNewMetadata()
                .withName(serviceName)
                .endMetadata()
                .withNewSpec()
                .withType("LoadBalancer")
                .addNewPort()
                .withProtocol("TCP")
                .withPort(80)
                .withNewTargetPort(80)
                .endPort()
                .addToSelector("app", block.name)
                .endSpec()
                .build()

            client.services().inNamespace(serviceName).resource(service).create()

            log.info { "VM Service created: $serviceName" }

            val ipAddress = service.status?.loadBalancer?.ingress?.firstOrNull()?.ip ?: ""
            val sshPrivateKey = "" // SSH private key 생성 등의 로직 필요

            val vmOutput = VirtualMachineOutput(createdPod.metadata?.uid!!, ipAddress, sshPrivateKey)

            return BlockOutput(block.id, block.type, block.virtualMachineFeatures?.region ?: "", vmOutput, null, null)
        }
    }
}
