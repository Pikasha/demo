package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class TestController {

    @Autowired
    private K8sService k8sService;

    @RequestMapping(value = "/t1")
    @ResponseBody
    public String createDeployment(@RequestParam(value = "namespace") String namespace, @RequestParam(value = "serviceName") String serviceName) {
        String result = k8sService.createDeployment(namespace, serviceName);
        return result;
    }

    @RequestMapping(value = "/t2")
    @ResponseBody
    public String createSecret() {
        String result = k8sService.createSecret();
        return result;
    }

}

