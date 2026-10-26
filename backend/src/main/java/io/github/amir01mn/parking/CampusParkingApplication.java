package io.github.amir01mn.parking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CampusParkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusParkingApplication.class, args);
    }
}
