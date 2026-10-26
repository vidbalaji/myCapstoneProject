package org.example.servicediscovery_aug2026;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class ServiceDiscoveryAug2026Application {

    public static void main(String[] args) {
        SpringApplication.run(ServiceDiscoveryAug2026Application.class, args);
    }

}
