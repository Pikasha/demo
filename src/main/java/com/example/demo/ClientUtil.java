package com.example.demo;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

@Component
public class ClientUtil {

    public static ApiClient client;

    static {
        KubeConfig config = null;
        try {
            config = KubeConfig.loadKubeConfig(new FileReader("/Users/shaqifan/IdeaProjects/demo/src/main/resources/config/k8s.config"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            client = ClientBuilder.kubeconfig(config).build();
            Configuration.setDefaultApiClient(client);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Configuration.setDefaultApiClient(client);
    }

    public ApiClient getClient() {
        if (client == null) {
            synchronized (ClientUtil.class) {
                if (client == null) {
                    KubeConfig config = null;
                    try {
                        config = KubeConfig.loadKubeConfig(new FileReader("/Users/shaqifan/IdeaProjects/demo/src/main/resources/config/k8s.config"));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    try {
                        client = ClientBuilder.kubeconfig(config).build();
                        Configuration.setDefaultApiClient(client);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Configuration.setDefaultApiClient(client);
                }
            }
        }
        return client;
    }

}
