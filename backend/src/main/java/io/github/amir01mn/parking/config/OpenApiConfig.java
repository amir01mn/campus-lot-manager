package io.github.amir01mn.parking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI campusParkingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Campus Parking API")
                .description("Booking, pricing and lot management for campus parking.")
                .version("v1")
                .license(new License().name("MIT")));
    }
}
