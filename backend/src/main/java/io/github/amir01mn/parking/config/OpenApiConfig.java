package io.github.amir01mn.parking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Name of the bearer-JWT scheme referenced by {@code @SecurityRequirement} on protected endpoints. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI campusParkingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Campus Parking API")
                        .description("Booking, pricing and lot management for campus parking. "
                                + "Get a token from POST /api/auth/login, then use \"Authorize\" to send it.")
                        .version("v1")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token returned by POST /api/auth/login")));
    }
}
