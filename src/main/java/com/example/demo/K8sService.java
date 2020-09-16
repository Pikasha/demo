package com.example.demo;

import com.google.common.collect.Maps;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.*;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class K8sService {

    private ApiClient client;
    private CoreApi api;
    private CoreV1Api v1Api;
    private ExtensionsV1beta1Api exApi;
    private AppsV1Api appsV1Api;

    public K8sService(ClientUtil clientUtil) {
        this.client = clientUtil.getClient();
        this.api = new CoreApi(this.client);
        this.v1Api = new CoreV1Api(this.client);
        this.exApi = new ExtensionsV1beta1Api(this.client);
        this.appsV1Api = new AppsV1Api(client);
    }

    public String createDeployment(String namespace, String serviceName) {
        CoreV1Api v1Api = this.v1Api;

        //namespace
        createNamespace(namespace);

        //deployment
        HashMap<String, String> label = Maps.newHashMap();
        label.put("app", "postgres");
        V1Deployment deployment = new V1DeploymentBuilder()
                .withApiVersion("apps/v1")
                .withKind("Deployment")
                .withMetadata(new V1ObjectMetaBuilder()
                        .withName("postgres-deployment" + new Random().nextInt(100))
                        .withNamespace(namespace)
                        .build())
                .withSpec(new V1DeploymentSpecBuilder()
                        .withSelector(new V1LabelSelectorBuilder()
                                .withMatchLabels(label)
                                .build())
                        .withReplicas(1)
                        .withTemplate(new V1PodTemplateSpecBuilder()
                                .withMetadata(new V1ObjectMetaBuilder().withNamespace(namespace).withLabels(label).build())
                                .withSpec(new V1PodSpecBuilder()
                                        .withNodeName("10.1.11.100-share")
                                        .withContainers(new V1ContainerBuilder()
                                                .withName("postgres-container")
                                                .withImage("postgres")
                                                .withImagePullPolicy("IfNotPresent")
                                                .withVolumeMounts(new V1VolumeMountBuilder().withName(namespace + "data").withMountPath("/temp-volume").build())
                                                .withPorts(new V1ContainerPortBuilder().withContainerPort(5432).build())
                                                .build())
                                        .withRestartPolicy("Always")
                                        .withVolumes(new V1VolumeBuilder()
                                                .withName(namespace + "data")
                                                .build())
                                        .build())
                                .build())
                        .withStrategy(new V1DeploymentStrategyBuilder().withType("Recreate").build())
                        .build())
                .build();
        try {
            appsV1Api.createNamespacedDeployment(namespace, deployment, null, null, null);
        } catch (ApiException e) {
            System.out.println("error in createNamespacedDeployment");
            e.printStackTrace();
            return "false";
        }

        //service
        V1Service service = new V1ServiceBuilder()
                .withApiVersion("v1")
                .withKind("Service")
                .withMetadata(new V1ObjectMetaBuilder()
                        .withName(serviceName)
                        .withNamespace(namespace)
                        .build())
                .withSpec(new V1ServiceSpecBuilder()
                        .withSelector(label)
                        .withPorts(new V1ServicePortBuilder().withProtocol("TCP").withPort(80).withTargetPort(new IntOrString(5432)).build())
                        .build())
                .build();
        try {
            v1Api.createNamespacedService(namespace, service, null, null, null);
        } catch (ApiException e) {
            System.out.println("error in createNamespacedService");
            e.printStackTrace();
            return "false";
        }

        //ingress
        try {
            boolean find = false;
            V1beta1IngressList ingressList = exApi.listNamespacedIngress(namespace, null, null, null, null, null, null, null, null, null);
            for (V1beta1Ingress item : ingressList.getItems()) {
                if (item.getMetadata().getName().equals("default.k8s")) {
                    find = true;
                    break;
                }
            }
            if (!find) {
                V1beta1IngressBackend backend = new V1beta1IngressBackendBuilder().withServiceName(serviceName).withServicePort(new IntOrString(80)).build();
                V1beta1HTTPIngressPath path = new V1beta1HTTPIngressPathBuilder().withPath("/").withBackend(backend).build();
                V1beta1HTTPIngressRuleValue ruleValue = new V1beta1HTTPIngressRuleValueBuilder().withPaths(path).build();
                V1beta1Ingress ingress = new V1beta1IngressBuilder()
                        .withApiVersion("extensions/v1beta1")
                        .withKind("Ingress")
                        .withMetadata(new V1ObjectMetaBuilder()
                                .withName("default.k8s")
                                .withNamespace(namespace)
                                .build())
                        .withSpec(new V1beta1IngressSpecBuilder()
                                .withRules(new V1beta1IngressRuleBuilder().withHttp(ruleValue).build())
                                .build())
                        .build();
                exApi.createNamespacedIngress(namespace, ingress, null, null, null);
            }
        } catch (ApiException e) {
            e.printStackTrace();
        }
        return "success : namespace = " + namespace + ", serviceName = " + serviceName;
    }

    private void createNamespace(String namespace) {
        try {
            boolean find = false;
            V1NamespaceList namespaceList = v1Api.listNamespace(null, null, null, null, null, null, null, null, null);
            for (V1Namespace item : namespaceList.getItems()) {
                if (item.getMetadata().getName().equals(namespace)) {
                    find = true;
                    break;
                }
            }
            if (!find) {
                v1Api.createNamespace(new V1NamespaceBuilder().withApiVersion("v1").withKind("Namespace").withMetadata(new V1ObjectMetaBuilder().withName(namespace).build()).build(), null, null, null);
                v1Api.createNamespacedResourceQuota(namespace, new V1ResourceQuotaBuilder()
                        .withApiVersion("v1")
                        .withKind("ResourceQuota")
                        .withMetadata(new V1ObjectMetaBuilder().withName("mem-cpu").build())
                        .withSpec(new V1ResourceQuotaSpecBuilder().withHard(getHard("1", "1Gi", "1", "1Gi")).build())
                        .build(), null, null, null);
            }
        } catch (ApiException e) {
            System.out.println("error in createNamespace");
            e.printStackTrace();
        }
    }

    private Map<String, Quantity> getHard(String requestCPU, String requestMemory, String limitCPU, String limitMemory) {
        HashMap<String, Quantity> hard = Maps.newHashMap();
        hard.put("requests.cpu", Quantity.fromString(requestCPU));
        hard.put("requests.memory", Quantity.fromString(requestMemory));
        hard.put("limits.cpu", Quantity.fromString(limitCPU));
        hard.put("limits.memory", Quantity.fromString(limitMemory));
        return hard;
    }

    private Map<String, Quantity> getResource(String cpu, String memory) {
        HashMap<String, Quantity> hard = Maps.newHashMap();
        hard.put("cpu", Quantity.fromString(cpu));
        hard.put("memory", Quantity.fromString(memory));
        return hard;
    }

    public String createSecret() {
        createNamespace("test-f");
        String username = RandomStringUtils.randomAlphabetic(8);
        String password = RandomStringUtils.randomAlphabetic(8);

        String secretName = "sec-demo" + new Random().nextInt(100);
        HashMap<String, byte[]> map = Maps.newHashMap();
        map.put("username", username.getBytes());
        map.put("password", password.getBytes());
        V1Secret secret = new V1SecretBuilder()
                .withApiVersion("v1")
                .withKind("Secret")
                .withMetadata(new V1ObjectMetaBuilder()
                        .withName(secretName)
                        .withNamespace("test-f")
                        .build())
                .withType("Opaque")
                .withData(map)
                .build();
        try {
            v1Api.createNamespacedSecret("test-f", secret, null, null, null);
        } catch (ApiException e) {
            System.out.println("error in createNamespacedSecret");
            e.printStackTrace();
            return "false";
        }

        V1Pod pod = new V1PodBuilder()
                .withApiVersion("v1")
                .withKind("Pod")
                .withMetadata(new V1ObjectMetaBuilder().withName(secretName).withNamespace("test-f").build())
                .withSpec(new V1PodSpecBuilder()
                        .withNodeName("10.1.11.100-share")
                        .withContainers(new V1ContainerBuilder()
                                .withName(secretName + "-container")
                                .withImage("nginx")
                                .withImagePullPolicy("IfNotPresent")
                                .withResources(new V1ResourceRequirementsBuilder()
                                        .withRequests(getResource("0.2", "200Mi"))
                                        .withLimits(getResource("0.5", "500Mi"))
                                        .build())
                                .withVolumeMounts(new V1VolumeMountBuilder().withName(secretName).withMountPath("/temp-volume").build())
                                .build())
                        .withVolumes(new V1VolumeBuilder()
                                .withName(secretName)
                                .withSecret(new V1SecretVolumeSourceBuilder().withSecretName(secretName).build())
                                .build())
                        .build())
                .build();
        try {
            v1Api.createNamespacedPod("test-f", pod, null, null, null);
        } catch (ApiException e) {
            System.out.println("error in createNamespacedPod");
            e.printStackTrace();
            return "false";
        }

        return "success : username = " + username + ", password = " + password;
    }

}
