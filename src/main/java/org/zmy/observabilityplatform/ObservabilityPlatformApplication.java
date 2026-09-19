package org.zmy.observabilityplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ObservabilityPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityPlatformApplication.class, args);
    }

}
