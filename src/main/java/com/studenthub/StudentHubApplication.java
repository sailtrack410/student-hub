package com.studenthub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StudentHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentHubApplication.class, args);
    }
}
